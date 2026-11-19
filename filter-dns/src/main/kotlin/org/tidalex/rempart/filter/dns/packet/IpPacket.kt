package org.tidalex.rempart.filter.dns.packet

import java.net.InetAddress

/**
 * Lecture / écriture minimales de paquets IPv4 + UDP/TCP tels qu'ils sortent
 * de l'interface TUN du VPN. Aucune dépendance Android : testable sur PC.
 */
object IpPacket {
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    data class Udp(
        val src: ByteArray, val dst: ByteArray,
        val srcPort: Int, val dstPort: Int,
        val payload: ByteArray,
    ) {
        val dstAddress: InetAddress get() = InetAddress.getByAddress(dst)
    }

    data class TcpSyn(val src: ByteArray, val dst: ByteArray, val srcPort: Int, val dstPort: Int, val seq: Long)

    sealed interface Parsed {
        data class UdpPacket(val udp: Udp) : Parsed
        data class TcpPacket(val syn: TcpSyn?) : Parsed
        data object Other : Parsed
    }

    fun version(buf: ByteArray, len: Int): Int = if (len < 1) 0 else (buf[0].toInt() ushr 4) and 0xF

    fun parse(buf: ByteArray, len: Int): Parsed {
        if (len < 20 || version(buf, len) != 4) return Parsed.Other
        val ihl = (buf[0].toInt() and 0x0F) * 4
        val totalLen = u16(buf, 2).coerceAtMost(len)
        if (ihl < 20 || totalLen < ihl + 8) return Parsed.Other
        // Fragments ignorés (offset != 0 ou MF) : une requête DNS n'est jamais fragmentée.
        val fragField = u16(buf, 6)
        if ((fragField and 0x3FFF) != 0) return Parsed.Other
        val proto = buf[9].toInt() and 0xFF
        val src = buf.copyOfRange(12, 16)
        val dst = buf.copyOfRange(16, 20)
        return when (proto) {
            PROTO_UDP -> {
                val udpLen = u16(buf, ihl + 4)
                if (udpLen < 8 || ihl + udpLen > totalLen) return Parsed.Other
                Parsed.UdpPacket(Udp(src, dst, u16(buf, ihl), u16(buf, ihl + 2), buf.copyOfRange(ihl + 8, ihl + udpLen)))
            }
            PROTO_TCP -> {
                if (totalLen < ihl + 20) return Parsed.Other
                val flags = buf[ihl + 13].toInt() and 0xFF
                val isSyn = (flags and 0x02) != 0 && (flags and 0x10) == 0
                Parsed.TcpPacket(if (isSyn) TcpSyn(src, dst, u16(buf, ihl), u16(buf, ihl + 2), u32(buf, ihl + 4)) else null)
            }
            else -> Parsed.Other
        }
    }

    /** Construit la réponse UDP (adresses et ports inversés). */
    fun buildUdpResponse(request: Udp, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        writeIpv4Header(p, total, PROTO_UDP, src = request.dst, dst = request.src)
        put16(p, 20, request.dstPort)
        put16(p, 22, request.srcPort)
        put16(p, 24, 8 + payload.size)
        put16(p, 26, 0)
        System.arraycopy(payload, 0, p, 28, payload.size)
        put16(p, 26, transportChecksum(p, 20, 8 + payload.size, PROTO_UDP).let { if (it == 0) 0xFFFF else it })
        return p
    }

    /**
     * Réponse TCP RST à un SYN : la connexion échoue immédiatement au lieu
     * d'attendre un délai d'expiration (les navigateurs repassent alors vite
     * au DNS classique, donc filtré, quand leur DoH est bloqué).
     */
    fun buildTcpReset(syn: TcpSyn): ByteArray {
        val total = 40
        val p = ByteArray(total)
        writeIpv4Header(p, total, PROTO_TCP, src = syn.dst, dst = syn.src)
        put16(p, 20, syn.dstPort)
        put16(p, 22, syn.srcPort)
        put32(p, 24, 0)
        put32(p, 28, (syn.seq + 1) and 0xFFFFFFFFL)
        p[32] = (5 shl 4).toByte()
        p[33] = 0x14 // RST + ACK
        put16(p, 34, 0)
        put16(p, 36, 0)
        put16(p, 38, 0)
        put16(p, 36, transportChecksum(p, 20, 20, PROTO_TCP))
        return p
    }

    private fun writeIpv4Header(p: ByteArray, total: Int, proto: Int, src: ByteArray, dst: ByteArray) {
        p[0] = 0x45
        p[1] = 0
        put16(p, 2, total)
        put16(p, 4, 0)
        put16(p, 6, 0x4000) // Don't Fragment
        p[8] = 64
        p[9] = proto.toByte()
        put16(p, 10, 0)
        System.arraycopy(src, 0, p, 12, 4)
        System.arraycopy(dst, 0, p, 16, 4)
        put16(p, 10, checksum(p, 0, 20, 0))
    }

    fun ipv4HeaderValid(p: ByteArray): Boolean = checksum(p, 0, 20, 0) == 0

    fun transportChecksum(p: ByteArray, off: Int, len: Int, proto: Int): Int {
        var sum = 0L
        for (i in 12 until 20 step 2) sum += u16(p, i)
        sum += proto
        sum += len
        return checksum(p, off, len, sum)
    }

    private fun checksum(p: ByteArray, off: Int, len: Int, initial: Long): Int {
        var sum = initial
        var i = off
        while (i + 1 < off + len) { sum += u16(p, i); i += 2 }
        if (i < off + len) sum += (p[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int): Long = (u16(b, o).toLong() shl 16) or u16(b, o + 2).toLong()
    private fun put16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
    private fun put32(b: ByteArray, o: Int, v: Long) { put16(b, o, (v ushr 16).toInt() and 0xFFFF); put16(b, o + 2, (v and 0xFFFF).toInt()) }
}
