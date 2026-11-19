package org.tidalex.rempart.filter.dns

/**
 * Résolveurs DNS publics souvent utilisés pour contourner un filtre (DoH, DoT,
 * DNS en dur). Leurs adresses sont routées dans le VPN :
 * - le DNS classique (UDP 53) vers elles est intercepté et FILTRÉ ;
 * - DoH (443), DoT (853) et QUIC vers elles sont coupés.
 * Les navigateurs (Chrome « DNS sécurisé », Firefox, Brave…) retombent alors sur
 * le DNS du système, donc sur Rempart. Le DNS privé d'Android en mode « strict »
 * est en plus bloqué par le garde des Réglages.
 */
object BypassResolvers {
    val IPV4 = listOf(
        // Cloudflare
        "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3",
        // Google
        "8.8.8.8", "8.8.4.4",
        // Quad9
        "9.9.9.9", "149.112.112.112", "9.9.9.10", "149.112.112.10", "9.9.9.11", "149.112.112.11",
        // AdGuard
        "94.140.14.14", "94.140.15.15", "94.140.14.15", "94.140.15.16", "94.140.14.140", "94.140.14.141",
        // OpenDNS / Cisco
        "208.67.222.222", "208.67.220.220", "208.67.222.123", "208.67.220.123",
        // CleanBrowsing
        "185.228.168.9", "185.228.169.9", "185.228.168.10", "185.228.169.11", "185.228.168.168", "185.228.169.168",
        // NextDNS, Control D, Mullvad, DNS.SB, Yandex, Comodo, Alibaba, 114DNS
        "45.90.28.0", "45.90.30.0", "76.76.2.0", "76.76.10.0", "194.242.2.2", "194.242.2.3",
        "185.222.222.222", "45.11.45.11", "77.88.8.8", "77.88.8.1", "8.26.56.26", "8.20.247.20",
        "223.5.5.5", "223.6.6.6", "114.114.114.114", "180.76.76.76",
    )

    val IPV6 = listOf(
        "2606:4700:4700::1111", "2606:4700:4700::1001", "2606:4700:4700::1113", "2606:4700:4700::1003",
        "2001:4860:4860::8888", "2001:4860:4860::8844",
        "2620:fe::fe", "2620:fe::9", "2620:fe::11",
        "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
        "2620:119:35::35", "2620:119:53::53",
    )

    /** Noms d'hôte DoH supplémentaires (en plus de la liste HaGeZi DoH embarquée). */
    val HOSTNAMES = listOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", "mozilla.cloudflare-dns.com", "one.one.one.one",
        "family.cloudflare-dns.com", "security.cloudflare-dns.com", "dns.quad9.net", "dns9.quad9.net",
        "dns.adguard.com", "dns.adguard-dns.com", "dns-family.adguard.com", "doh.opendns.com",
        "dns.nextdns.io", "doh.cleanbrowsing.org", "doh.mullvad.net", "dns.mullvad.net", "doh.dns.sb",
        "chrome.cloudflare-dns.com", "doh.xfinity.com", "dns.controld.com", "freedns.controld.com",
    )
}
