package org.tidalex.rempart.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainBlocklistTest {

    private val list = DomainBlocklist.of(listOf("badsite.com", "adult.example.org", "xn--e1afmkfd.com"))

    @Test fun `exact match`() = assertThat(list.matches("badsite.com")).isTrue()

    @Test fun `subdomains are blocked (wildcard)`() {
        assertThat(list.matches("www.badsite.com")).isTrue()
        assertThat(list.matches("a.b.c.badsite.com")).isTrue()
    }

    @Test fun `parent of a listed subdomain is not blocked`() {
        assertThat(list.matches("example.org")).isFalse()
        assertThat(list.matches("adult.example.org")).isTrue()
    }

    @Test fun `similar but different domains are not blocked`() {
        assertThat(list.matches("notbadsite.com")).isFalse()
        assertThat(list.matches("badsite.co")).isFalse()
    }

    // --- Contournements ---
    @Test fun `case, trailing dot, port, scheme and path do not bypass`() {
        assertThat(list.matches("BadSite.COM")).isTrue()
        assertThat(list.matches("badsite.com.")).isTrue()
        assertThat(list.matches("badsite.com:443")).isTrue()
        assertThat(list.matches("https://user@www.badsite.com:8080/path?q=1")).isTrue()
    }

    @Test fun `unicode (IDN) spelling is converted to punycode`() {
        assertThat(list.matches("пример.com")).isTrue()
    }

    @Test fun `parses hosts, adblock and wildcard formats`() {
        val text = """
            # commentaire
            ! commentaire adblock
            0.0.0.0 one.com
            127.0.0.1 two.com # fin de ligne
            ||three.com^
            *.four.com
            five.com
            0.0.0.0 localhost
            not a domain
        """.trimIndent()
        val l = DomainBlocklist.parse(text.reader().buffered()).build()
        assertThat(l.size).isEqualTo(5)
        listOf("one.com", "two.com", "three.com", "x.four.com", "five.com").forEach { assertThat(l.matches(it)).isTrue() }
        assertThat(l.matches("localhost")).isFalse()
    }

    @Test fun `duplicates are removed`() {
        val l = DomainBlocklist.of(listOf("a.com", "A.com", "a.com."))
        assertThat(l.size).isEqualTo(1)
    }

    @Test fun `hash roundtrip`() {
        val again = DomainBlocklist.fromHashes(list.toLongArray())
        assertThat(again.matches("www.badsite.com")).isTrue()
    }

    @Test fun `invalid inputs never crash`() {
        listOf("", ".", "..", "[::1]", "a", "a..b", "x".repeat(300) + ".com", "\u0000.com").forEach {
            assertThat(list.matches(it)).isFalse()
        }
    }
}

class NeverBlockTest {
    @Test fun `infrastructure domains are never blocked whole, their listed subdomains are`() {
        val l = DomainBlocklist.of(listOf("google.com", "githubusercontent.com", "adult-pics.googleusercontent.com"))
        assertThat(l.matches("www.google.com")).isFalse()
        assertThat(l.matches("raw.githubusercontent.com")).isFalse()
        assertThat(l.matches("adult-pics.googleusercontent.com")).isTrue()
    }
}
