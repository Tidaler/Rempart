package org.tidalex.rempart.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeSearchTest {
    @Test fun `google country domains are forced to safesearch`() {
        listOf("www.google.com", "google.fr", "www.google.cm", "www.google.co.uk", "www.google.com.br", "WWW.GOOGLE.COM.")
            .forEach { assertThat(SafeSearch.rewriteTarget(it)).isEqualTo(SafeSearch.GOOGLE) }
    }

    @Test fun `other engines`() {
        assertThat(SafeSearch.rewriteTarget("www.bing.com")).isEqualTo(SafeSearch.BING)
        assertThat(SafeSearch.rewriteTarget("duckduckgo.com")).isEqualTo(SafeSearch.DUCKDUCKGO)
        assertThat(SafeSearch.rewriteTarget("m.youtube.com")).isEqualTo(SafeSearch.YOUTUBE)
    }

    @Test fun `youtube restriction can be turned off`() =
        assertThat(SafeSearch.rewriteTarget("www.youtube.com", youtubeRestricted = false)).isNull()

    @Test fun `non search hosts untouched`() {
        listOf("mail.google.com", "maps.google.com", "googleapis.com", "forcesafesearch.google.com", "example.com")
            .forEach { assertThat(SafeSearch.rewriteTarget(it)).isNull() }
    }
}
