package org.tidalex.rempart.filter.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Résolveur amont. Par défaut un résolveur « famille » qui filtre aussi
 * le contenu adulte : double protection si une adresse manque à nos listes.
 */
enum class UpstreamChoice(val label: String, val servers: List<String>) {
    CLOUDFLARE_FAMILY("Cloudflare for Families (1.1.1.3)", listOf("1.1.1.3", "1.0.0.3")),
    ADGUARD_FAMILY("AdGuard DNS Family", listOf("94.140.14.15", "94.140.15.16")),
    CLEANBROWSING_ADULT("CleanBrowsing Adult", listOf("185.228.168.10", "185.228.169.11")),
    ;

    companion object { val DEFAULT = CLOUDFLARE_FAMILY }
}

/**
 * Transmission UDP vers les résolveurs amont, avec repli sur le serveur suivant.
 * [protect] est `VpnService.protect` : la socket sort du VPN, sinon elle tournerait en boucle.
 */
class UdpUpstream(
    private val servers: List<InetAddress>,
    private val protect: (DatagramSocket) -> Boolean,
    private val timeoutMs: Int = 4_000,
    private val port: Int = 53,
) : DnsUpstream {

    override fun query(message: ByteArray): ByteArray? {
        for (server in servers) {
            try {
                DatagramSocket().use { socket ->
                    if (!protect(socket)) return@use
                    socket.soTimeout = timeoutMs
                    socket.send(DatagramPacket(message, message.size, InetSocketAddress(server, port)))
                    val buf = ByteArray(4096)
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    if (resp.length >= 12 && buf[0] == message[0] && buf[1] == message[1]) {
                        return buf.copyOf(resp.length)
                    }
                }
            } catch (e: SocketTimeoutException) {
                // serveur suivant
            } catch (e: java.io.IOException) {
                // réseau indisponible : serveur suivant
            }
        }
        return null
    }

    companion object {
        fun of(choice: UpstreamChoice, protect: (DatagramSocket) -> Boolean) =
            UdpUpstream(choice.servers.map { InetAddress.getByName(it) }, protect)
    }
}
