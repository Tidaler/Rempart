package org.tidalex.rempart.filter.dns

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist
import org.tidalex.rempart.core.domain.SafeSearch
import org.tidalex.rempart.filter.dns.packet.DnsMessage
import org.tidalex.rempart.filter.dns.packet.IpPacket
import java.net.InetAddress

class DnsFilterEngineTest {

    /** Résolveur simulé : connaît quelques adresses, enregistre les noms demandés. */
    private class FakeUpstream : DnsUpstream {
        val asked = mutableListOf<String>()
        var cname: Pair<String, String>? = null
        override fun query(message: ByteArray): ByteArray {
            val name = DnsMessage.parseQuery(message).question.name
            asked += name
            val ip = when (name) {
                SafeSearch.GOOGLE -> "216.239.38.120"
                SafeSearch.YOUTUBE -> "216.239.38.119"
                else -> "93.184.216.34"
            }
            return TestPackets.upstreamAnswer(message, ip, cname?.takeIf { it.first == name }?.second)
        }
    }

    private val upstream = FakeUpstream()
    private val policy = ContentPolicy(
        DomainBlocklist.of(listOf("adultsite.com", "hidden-adult.net")),
        DomainBlocklist.of(BypassResolvers.HOSTNAMES),
    )
    private val engine = DnsFilterEngine(policy, upstream)

    private fun ask(name: String, type: Int = DnsMessage.TYPE_A): Pair<Int, List<DnsMessage.Record>> {
        val resp = engine.handleDns(TestPackets.dnsQuery(name, type))!!
        return DnsMessage.rcode(resp) to DnsMessage.parseAnswers(resp)
    }

    @Test fun `blocked domain answers 0_0_0_0 without asking upstream`() {
        val (rcode, ans) = ask("www.adultsite.com")
        assertThat(rcode).isEqualTo(0)
        assertThat(ans.single().rdata).isEqualTo(ByteArray(4))
        assertThat(upstream.asked).isEmpty()
        assertThat(engine.blockedCount.get()).isEqualTo(1)
    }

    @Test fun `blocked AAAA answers ipv6 unspecified and HTTPS records are empty`() {
        assertThat(ask("adultsite.com", DnsMessage.TYPE_AAAA).second.single().rdata).isEqualTo(ByteArray(16))
        assertThat(ask("adultsite.com", DnsMessage.TYPE_HTTPS).second).isEmpty()
    }

    @Test fun `allowed domain is forwarded`() {
        val (_, ans) = ask("www.wikipedia.org")
        assertThat(InetAddress.getByAddress(ans.single().rdata).hostAddress).isEqualTo("93.184.216.34")
        assertThat(upstream.asked).containsExactly("www.wikipedia.org")
    }

    @Test fun `google is forced to safesearch with the original name kept`() {
        val (_, ans) = ask("www.google.fr")
        assertThat(upstream.asked).containsExactly(SafeSearch.GOOGLE)
        assertThat(ans.single().name).isEqualTo("www.google.fr")
        assertThat(InetAddress.getByAddress(ans.single().rdata).hostAddress).isEqualTo("216.239.38.120")
    }

    @Test fun `youtube is restricted`() {
        ask("m.youtube.com")
        assertThat(upstream.asked).containsExactly(SafeSearch.YOUTUBE)
    }

    @Test fun `safesearch off forwards normally`() {
        engine.safeSearch = false
        ask("www.google.com")
        assertThat(upstream.asked).containsExactly("www.google.com")
    }

    // --- Sécurité : contournements ---

    @Test fun `DoH provider hostnames are blocked (Chrome secure DNS, apps)`() {
        assertThat(ask("dns.google").second.single().rdata).isEqualTo(ByteArray(4))
        assertThat(ask("mozilla.cloudflare-dns.com").second.single().rdata).isEqualTo(ByteArray(4))
    }

    @Test fun `firefox DoH canary returns NXDOMAIN`() {
        assertThat(ask("use-application-dns.net").first).isEqualTo(DnsMessage.RCODE_NXDOMAIN)
    }

    @Test fun `CNAME cloaking to a blocked domain is caught`() {
        upstream.cname = "innocent-looking.org" to "cdn.hidden-adult.net"
        assertThat(ask("innocent-looking.org").second.single().rdata).isEqualTo(ByteArray(4))
    }

    @Test fun `hardcoded plain DNS to 8_8_8_8 is filtered too`() {
        val q = TestPackets.dnsQuery("adultsite.com")
        val pkt = TestPackets.ipv4Udp(TestPackets.CLIENT, byteArrayOf(8, 8, 8, 8), 5555, 53, q)
        val reply = engine.handlePacket(pkt, pkt.size)!!
        val udp = (IpPacket.parse(reply, reply.size) as IpPacket.Parsed.UdpPacket).udp
        assertThat(udp.src).isEqualTo(byteArrayOf(8, 8, 8, 8)) // la réponse semble venir de 8.8.8.8
        assertThat(DnsMessage.parseAnswers(udp.payload).single().rdata).isEqualTo(ByteArray(4))
    }

    @Test fun `DoH and DoT connections to known resolvers are reset`() {
        listOf(443, 853).forEach { port ->
            val syn = TestPackets.ipv4TcpSyn(byteArrayOf(1, 1, 1, 1), port)
            val rst = engine.handlePacket(syn, syn.size)!!
            assertThat(rst[33].toInt() and 0x04).isNotEqualTo(0)
        }
    }

    @Test fun `QUIC to a resolver is dropped`() {
        val pkt = TestPackets.ipv4Udp(TestPackets.CLIENT, byteArrayOf(1, 1, 1, 1), 5000, 443, ByteArray(100))
        assertThat(engine.handlePacket(pkt, pkt.size)).isNull()
    }

    @Test fun `upstream failure gives SERVFAIL, never an unfiltered answer`() {
        val failing = DnsFilterEngine(policy, { null })
        val resp = failing.handleDns(TestPackets.dnsQuery("example.org"))!!
        assertThat(DnsMessage.rcode(resp)).isEqualTo(DnsMessage.RCODE_SERVFAIL)
    }

    @Test fun `keyword heuristic blocks brand-new adult domains not yet listed`() {
        assertThat(ask("hot-new-porn-2026.top").second.single().rdata).isEqualTo(ByteArray(4))
    }

    @Test fun `bypass resolver list is routed for both families`() {
        assertThat(BypassResolvers.IPV4).containsAtLeast("1.1.1.1", "8.8.8.8", "9.9.9.9", "94.140.14.14")
        BypassResolvers.IPV4.forEach { InetAddress.getByName(it) }
        BypassResolvers.IPV6.forEach { InetAddress.getByName(it) }
    }

    // --- Performance ---
    @Test fun `100k blocked queries are answered quickly`() {
        val big = ContentPolicy(DomainBlocklist.of((0 until 150_000).map { "porn-free-site$it.com" } + "adultsite.com"))
        val e = DnsFilterEngine(big, upstream)
        val q = TestPackets.ipv4Udp(TestPackets.CLIENT, TestPackets.TUN_DNS, 4000, 53, TestPackets.dnsQuery("www.adultsite.com"))
        repeat(5_000) { e.handlePacket(q, q.size) }
        val t0 = System.nanoTime()
        repeat(100_000) { e.handlePacket(q, q.size) }
        val usPer = (System.nanoTime() - t0) / 1e3 / 100_000
        assertThat(usPer).isLessThan(50.0) // < 50 µs par requête bloquée sur PC
    }
}
