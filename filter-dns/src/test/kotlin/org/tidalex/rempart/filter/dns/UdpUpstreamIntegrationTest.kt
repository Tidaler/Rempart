package org.tidalex.rempart.filter.dns

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist
import org.tidalex.rempart.filter.dns.packet.DnsMessage
import org.tidalex.rempart.filter.dns.packet.IpPacket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Test d'intégration : un vrai serveur DNS UDP local joue le résolveur amont,
 * le moteur reçoit des paquets IP complets comme ceux du TUN.
 */
class UdpUpstreamIntegrationTest {
    private lateinit var server: DatagramSocket
    private lateinit var serverThread: Thread
    @Volatile private var received = 0

    @Before fun startServer() {
        server = DatagramSocket(0, InetAddress.getLoopbackAddress())
        serverThread = thread(isDaemon = true) {
            val buf = ByteArray(1500)
            while (!server.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { server.receive(p) }.onFailure { return@thread }
                received++
                val answer = TestPackets.upstreamAnswer(buf.copyOf(p.length), "203.0.113.7")
                server.send(DatagramPacket(answer, answer.size, p.socketAddress))
            }
        }
    }

    @After fun stop() { server.close() }

    private fun upstreamTo(port: Int, protectOk: Boolean = true) =
        UdpUpstream(listOf(InetAddress.getLoopbackAddress()), { protectOk }, timeoutMs = 1000, port = port)

    @Test fun `end to end forward through a real UDP resolver`() {
        val engine = DnsFilterEngine(ContentPolicy(DomainBlocklist.of(listOf("adultsite.com"))), upstreamTo(server.localPort))
        val pkt = TestPackets.ipv4Udp(TestPackets.CLIENT, TestPackets.TUN_DNS, 33333, 53, TestPackets.dnsQuery("example.org"))
        val reply = engine.handlePacket(pkt, pkt.size)!!
        val udp = (IpPacket.parse(reply, reply.size) as IpPacket.Parsed.UdpPacket).udp
        assertThat(InetAddress.getByAddress(DnsMessage.parseAnswers(udp.payload).single().rdata).hostAddress).isEqualTo("203.0.113.7")
        assertThat(received).isEqualTo(1)

        // Domaine bloqué : le résolveur amont n'est JAMAIS contacté (confidentialité).
        val blocked = TestPackets.ipv4Udp(TestPackets.CLIENT, TestPackets.TUN_DNS, 33334, 53, TestPackets.dnsQuery("adultsite.com"))
        engine.handlePacket(blocked, blocked.size)
        assertThat(received).isEqualTo(1)
    }

    @Test fun `unreachable upstream falls back then SERVFAIL`() {
        val dead = DatagramSocket().let { s -> val p = s.localPort; s.close(); p }
        val up = UdpUpstream(listOf(InetAddress.getLoopbackAddress()), { true }, timeoutMs = 300, port = dead)
        assertThat(up.query(TestPackets.dnsQuery("example.org"))).isNull()
    }

    @Test fun `if the socket cannot be protected, nothing is sent (no routing loop)`() {
        assertThat(upstreamTo(server.localPort, protectOk = false).query(TestPackets.dnsQuery("example.org"))).isNull()
        assertThat(received).isEqualTo(0)
    }
}
