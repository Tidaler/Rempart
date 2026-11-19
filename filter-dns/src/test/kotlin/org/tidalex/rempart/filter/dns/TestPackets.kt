package org.tidalex.rempart.filter.dns

import org.tidalex.rempart.filter.dns.packet.DnsMessage
import java.io.ByteArrayOutputStream
import java.net.InetAddress

/** Fabrique de paquets pour les tests (ce qu'enverrait une appli via le TUN). */
object TestPackets {
    val CLIENT = byteArrayOf(10, 111, 222.toByte(), 1)
    val TUN_DNS = byteArrayOf(10, 111, 222.toByte(), 2)

    fun ipv4Udp(src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 28 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45; p[2] = (total ushr 8).toByte(); p[3] = total.toByte(); p[8] = 64; p[9] = 17
        System.arraycopy(src, 0, p, 12, 4); System.arraycopy(dst, 0, p, 16, 4)
        p[20] = (srcPort ushr 8).toByte(); p[21] = srcPort.toByte()
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        p[24] = ((8 + payload.size) ushr 8).toByte(); p[25] = (8 + payload.size).toByte()
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    fun ipv4TcpSyn(dst: ByteArray, dstPort: Int, seq: Long = 1000): ByteArray {
        val p = ByteArray(40)
        p[0] = 0x45; p[3] = 40; p[8] = 64; p[9] = 6
        System.arraycopy(CLIENT, 0, p, 12, 4); System.arraycopy(dst, 0, p, 16, 4)
        p[20] = 0xC3.toByte(); p[21] = 0x50 // port source 50000
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        p[24] = (seq ushr 24).toByte(); p[25] = (seq ushr 16).toByte(); p[26] = (seq ushr 8).toByte(); p[27] = seq.toByte()
        p[32] = 0x50; p[33] = 0x02 // SYN
        return p
    }

    fun dnsQuery(name: String, type: Int = DnsMessage.TYPE_A, id: Int = 0x1234) = DnsMessage.buildQuery(id, name, type)

    /** Réponse amont simulée : A/AAAA pour le nom demandé, éventuellement précédé d'un CNAME. */
    fun upstreamAnswer(query: ByteArray, address: String, cnameTo: String? = null): ByteArray {
        val q = DnsMessage.parseQuery(query)
        val addr = InetAddress.getByName(address).address
        val records = mutableListOf<DnsMessage.Record>()
        if (cnameTo != null) records += DnsMessage.Record(q.question.name, DnsMessage.TYPE_CNAME, 300, encodeName(cnameTo))
        records += DnsMessage.Record(q.question.name, if (addr.size == 4) DnsMessage.TYPE_A else DnsMessage.TYPE_AAAA, 300, addr)
        return DnsMessage.response(query, q, DnsMessage.RCODE_NOERROR, records)
    }

    fun encodeName(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        name.split('.').forEach { out.write(it.length); out.write(it.toByteArray()) }
        out.write(0)
        return out.toByteArray()
    }
}
