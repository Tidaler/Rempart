package org.tidalex.rempart.filter.dns

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tidalex.rempart.filter.dns.packet.DnsMessage
import org.tidalex.rempart.filter.dns.packet.IpPacket
import kotlin.random.Random

class PacketCodecTest {

    @Test fun `parse a DNS query packet`() {
        val pkt = TestPackets.ipv4Udp(TestPackets.CLIENT, TestPackets.TUN_DNS, 40000, 53, TestPackets.dnsQuery("www.example.com"))
        val parsed = IpPacket.parse(pkt, pkt.size) as IpPacket.Parsed.UdpPacket
        assertThat(parsed.udp.dstPort).isEqualTo(53)
        val q = DnsMessage.parseQuery(parsed.udp.payload)
        assertThat(q.question.name).isEqualTo("www.example.com")
        assertThat(q.id).isEqualTo(0x1234)
    }

    @Test fun `UDP response swaps addresses and ports and has valid checksums`() {
        val pkt = TestPackets.ipv4Udp(TestPackets.CLIENT, TestPackets.TUN_DNS, 40000, 53, ByteArray(10))
        val udp = (IpPacket.parse(pkt, pkt.size) as IpPacket.Parsed.UdpPacket).udp
        val resp = IpPacket.buildUdpResponse(udp, byteArrayOf(1, 2, 3))
        assertThat(IpPacket.ipv4HeaderValid(resp)).isTrue()
        assertThat(resp.copyOfRange(12, 16)).isEqualTo(TestPackets.TUN_DNS)
        assertThat(resp.copyOfRange(16, 20)).isEqualTo(TestPackets.CLIENT)
        assertThat(IpPacket.u16(resp, 20)).isEqualTo(53)
        assertThat(IpPacket.u16(resp, 22)).isEqualTo(40000)
        // Somme de contrôle UDP vérifiée : recalcul sur le paquet complet = 0.
        assertThat(IpPacket.transportChecksum(resp, 20, resp.size - 20, IpPacket.PROTO_UDP)).isEqualTo(0)
    }

    @Test fun `TCP RST answers a SYN with seq+1`() {
        val syn = TestPackets.ipv4TcpSyn(byteArrayOf(1, 1, 1, 1), 443, seq = 41)
        val parsed = IpPacket.parse(syn, syn.size) as IpPacket.Parsed.TcpPacket
        val rst = IpPacket.buildTcpReset(parsed.syn!!)
        assertThat(rst[33].toInt() and 0x04).isNotEqualTo(0)
        assertThat(IpPacket.u16(rst, 30)).isEqualTo(42)
        assertThat(IpPacket.ipv4HeaderValid(rst)).isTrue()
        assertThat(IpPacket.transportChecksum(rst, 20, 20, IpPacket.PROTO_TCP)).isEqualTo(0)
    }

    @Test fun `compressed names in answers are read`() {
        val query = TestPackets.dnsQuery("a.example.com")
        val answer = TestPackets.upstreamAnswer(query, "93.184.216.34", cnameTo = "cdn.example.net")
        val records = DnsMessage.parseAnswers(answer)
        assertThat(records.map { it.type }).containsExactly(DnsMessage.TYPE_CNAME, DnsMessage.TYPE_A).inOrder()
        assertThat(records[0].name).isEqualTo("a.example.com")
        assertThat(records[0].target).isEqualTo("cdn.example.net")
    }

    @Test fun `compression loops are rejected`() {
        val msg = ByteArray(14)
        msg[5] = 1; msg[12] = 0xC0.toByte(); msg[13] = 12 // pointeur vers lui-même
        runCatching { DnsMessage.parseQuery(msg) }.let { assertThat(it.exceptionOrNull()).isInstanceOf(DnsMessage.MalformedException::class.java) }
    }

    // --- Sécurité : fuzzing ---
    @Test fun `random garbage never crashes the parsers`() {
        val rnd = Random(42)
        repeat(20_000) {
            val len = rnd.nextInt(0, 120)
            val b = rnd.nextBytes(len)
            if (len > 0 && rnd.nextBoolean()) b[0] = 0x45
            IpPacket.parse(b, len)
            runCatching { DnsMessage.parseQuery(b) }.exceptionOrNull()?.let { assertThat(it).isInstanceOf(DnsMessage.MalformedException::class.java) }
            runCatching { DnsMessage.parseAnswers(b) }.exceptionOrNull()?.let { assertThat(it).isInstanceOf(DnsMessage.MalformedException::class.java) }
        }
    }

    @Test fun `non IPv4 and fragments are ignored`() {
        val v6 = ByteArray(60).also { it[0] = 0x60 }
        assertThat(IpPacket.parse(v6, v6.size)).isEqualTo(IpPacket.Parsed.Other)
        val frag = TestPackets.ipv4Udp(TestPackets.CLIENT, TestPackets.TUN_DNS, 1, 53, ByteArray(20)).also { it[6] = 0x20 }
        assertThat(IpPacket.parse(frag, frag.size)).isEqualTo(IpPacket.Parsed.Other)
    }
}
