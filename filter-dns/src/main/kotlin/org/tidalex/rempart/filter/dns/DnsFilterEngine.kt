package org.tidalex.rempart.filter.dns

import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.SafeSearch
import org.tidalex.rempart.core.domain.Verdict
import org.tidalex.rempart.filter.dns.packet.DnsMessage
import org.tidalex.rempart.filter.dns.packet.IpPacket
import java.util.concurrent.atomic.AtomicLong

/** Envoi d'une requête DNS brute à un résolveur amont ; renvoie la réponse brute ou null. */
fun interface DnsUpstream {
    fun query(message: ByteArray): ByteArray?
}

/**
 * Cœur du filtre réseau, indépendant d'Android : reçoit un paquet IP sorti du
 * TUN, renvoie le paquet IP à réinjecter (ou null pour l'ignorer).
 *
 * Règles :
 * 1. Requête DNS vers un domaine interdit → 0.0.0.0 / ::.
 * 2. Moteur de recherche → réponse avec les adresses de la version « SafeSearch ».
 * 3. `use-application-dns.net` → NXDOMAIN (désactive le DoH par défaut de Firefox).
 * 4. Sinon → transmis au résolveur amont ; la réponse est vérifiée (CNAME vers un domaine interdit → bloqué).
 * 5. Toute autre connexion vers un résolveur DoH/DoT connu (routé dans le TUN) → TCP RST / ignorée.
 */
class DnsFilterEngine(
    private val policy: ContentPolicy,
    private val upstream: DnsUpstream,
    @Volatile var safeSearch: Boolean = true,
    @Volatile var youtubeRestricted: Boolean = true,
) {
    val blockedCount = AtomicLong()
    val forwardedCount = AtomicLong()

    fun handlePacket(buf: ByteArray, len: Int): ByteArray? = when (val p = IpPacket.parse(buf, len)) {
        is IpPacket.Parsed.UdpPacket -> {
            if (p.udp.dstPort == 53) handleDns(p.udp.payload)?.let { IpPacket.buildUdpResponse(p.udp, it) }
            else null // QUIC / DoH3 vers un résolveur bloqué : on ignore
        }
        is IpPacket.Parsed.TcpPacket -> p.syn?.let { IpPacket.buildTcpReset(it) } // DoH / DoT bloqué
        IpPacket.Parsed.Other -> null
    }

    /** Traite une requête DNS brute, renvoie la réponse brute. */
    fun handleDns(query: ByteArray): ByteArray? {
        val q = try { DnsMessage.parseQuery(query) } catch (e: DnsMessage.MalformedException) { return null }
        val name = q.question.name

        if (name == "use-application-dns.net") return DnsMessage.errorResponse(query, q, DnsMessage.RCODE_NXDOMAIN)

        if (policy.checkHost(name) is Verdict.Block) {
            blockedCount.incrementAndGet()
            return DnsMessage.blockedResponse(query, q)
        }

        if (safeSearch) {
            SafeSearch.rewriteTarget(name, youtubeRestricted)?.let { target -> return safeSearchResponse(query, q, target) }
        }

        forwardedCount.incrementAndGet()
        val response = upstream.query(query) ?: return DnsMessage.errorResponse(query, q, DnsMessage.RCODE_SERVFAIL)
        return if (responseLeadsToBlocked(response)) {
            blockedCount.incrementAndGet()
            DnsMessage.blockedResponse(query, q)
        } else response
    }

    private fun safeSearchResponse(query: ByteArray, q: DnsMessage.Query, target: String): ByteArray {
        val type = q.question.type
        if (type != DnsMessage.TYPE_A && type != DnsMessage.TYPE_AAAA) {
            // HTTPS/SVCB etc. : réponse vide pour forcer le navigateur à utiliser A/AAAA.
            return DnsMessage.response(query, q, DnsMessage.RCODE_NOERROR, emptyList())
        }
        val sub = DnsMessage.buildQuery(q.id, target, type)
        val raw = upstream.query(sub) ?: return DnsMessage.errorResponse(query, q, DnsMessage.RCODE_SERVFAIL)
        val records = try { DnsMessage.parseAnswers(raw) } catch (e: DnsMessage.MalformedException) { emptyList() }
        val addresses = records.filter { it.type == type }.map { it.copy(ttl = it.ttl.coerceIn(60, 3600)) }
        return DnsMessage.response(query, q, DnsMessage.RCODE_NOERROR, addresses)
    }

    private fun responseLeadsToBlocked(response: ByteArray): Boolean = try {
        DnsMessage.parseAnswers(response).any { r ->
            (r.target != null && policy.checkHost(r.target) is Verdict.Block) ||
                (r.name.isNotEmpty() && policy.checkHost(r.name) is Verdict.Block)
        }
    } catch (e: DnsMessage.MalformedException) {
        false
    }
}
