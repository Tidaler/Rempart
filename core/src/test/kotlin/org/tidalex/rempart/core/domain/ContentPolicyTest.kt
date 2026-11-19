package org.tidalex.rempart.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentPolicyTest {

    private val policy = ContentPolicy(
        blocklist = DomainBlocklist.of(listOf("listedsite.net")),
        bypassList = DomainBlocklist.of(listOf("dns.google", "cloudflare-dns.com")),
    )

    @Test fun `blocklisted host`() =
        assertThat(policy.checkHost("m.listedsite.net")).isEqualTo(Verdict.Block(BlockReason.BLOCKLIST))

    @Test fun `unlisted adult-looking host is caught by keywords`() {
        assertThat(policy.checkHost("brand-new-porn-site.xyz")).isEqualTo(Verdict.Block(BlockReason.KEYWORD_HOST))
        assertThat(policy.checkHost("free-sex.club")).isEqualTo(Verdict.Block(BlockReason.KEYWORD_HOST))
    }

    @Test fun `legitimate hosts are allowed (no false positives)`() {
        listOf(
            "www.essex.ac.uk", "www.sussex.ac.uk", "analytics.google.com", "therapist.com", "wikipedia.org",
            "www.lemonde.fr", "middlesexhospital.org", "sextant-maritime.fr", "www.ameli.fr", "expressen.se",
        ).forEach { assertThat(policy.checkHost(it)).isEqualTo(Verdict.Allow) }
    }

    @Test fun `DoH resolvers are blocked as bypass`() =
        assertThat(policy.checkHost("dns.google")).isEqualTo(Verdict.Block(BlockReason.DNS_BYPASS))

    @Test fun `URL bar text in private mode is checked like any other`() {
        // En navigation privée la barre d'URL reste lisible par l'accessibilité.
        assertThat(policy.checkText("listedsite.net/video/123")).isInstanceOf(Verdict.Block::class.java)
        assertThat(policy.checkText("https://www.listedsite.net")).isInstanceOf(Verdict.Block::class.java)
    }

    @Test fun `explicit searches are blocked`() {
        assertThat(policy.checkText("vidéo sexe gratuite")).isEqualTo(Verdict.Block(BlockReason.KEYWORD_TEXT))
        assertThat(policy.checkText("Free PORN videos")).isInstanceOf(Verdict.Block::class.java)
        assertThat(policy.checkText("femme nue")).isEqualTo(Verdict.Block(BlockReason.KEYWORD_TEXT))
    }

    @Test fun `ordinary text is allowed`() {
        listOf(
            "Météo à Douala demain", "éducation à la sexualité au collège", "avenue de la République",
            "recette du ndolé", "cancer du sein dépistage", "Middlesex University", "asexué définition",
        ).forEach { assertThat(policy.checkText(it)).isEqualTo(Verdict.Allow) }
    }

    @Test fun `URL extractor finds hosts and ignores file names`() {
        assertThat(UrlExtractor.hosts("go to www.Example.com/page and http://a.b.org:8080/x, photo.jpg"))
            .containsExactly("www.example.com", "a.b.org")
    }
}
