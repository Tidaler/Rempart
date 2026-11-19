package org.tidalex.rempart.filter.accessibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tidalex.rempart.core.domain.BlockReason
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist

/** Faux nœud pour simuler n'importe quel écran sans téléphone. */
private data class FakeNode(
    override val text: CharSequence? = null,
    override val viewId: String? = null,
    override val className: CharSequence? = "android.widget.TextView",
    override val contentDescription: CharSequence? = null,
    val children: List<FakeNode> = emptyList(),
) : NodeView {
    override val childCount get() = children.size
    override fun child(i: Int) = children[i]
}

private fun screen(vararg nodes: FakeNode) = FakeNode(className = "android.widget.FrameLayout", children = nodes.toList())
private fun urlBar(pkg: String, id: String, url: String) = FakeNode(text = url, viewId = "$pkg:id/$id", className = "android.widget.EditText")
private fun webView(vararg texts: String) = FakeNode(className = "android.webkit.WebView", children = texts.map { FakeNode(text = it) })

class ScreenAnalyzerTest {
    private val policy = ContentPolicy(DomainBlocklist.of(listOf("adultsite.com", "suggestive-pics.net")))
    private val analyzer = ScreenAnalyzer({ policy })

    @Test fun `chrome url bar with blocked site`() {
        val d = analyzer.analyze("com.android.chrome", screen(urlBar("com.android.chrome", "url_bar", "adultsite.com/video")), false)
        assertThat(d).isEqualTo(ScreenDecision.BlockContent(BlockReason.BLOCKLIST))
    }

    @Test fun `incognito tab is handled the same way`() {
        val incognito = screen(FakeNode(text = "Navigation privée"), urlBar("com.android.chrome", "url_bar", "https://www.adultsite.com"))
        assertThat(analyzer.analyze("com.android.chrome", incognito, false)).isInstanceOf(ScreenDecision.BlockContent::class.java)
    }

    @Test fun `firefox, samsung, opera, brave, edge, duckduckgo url bars`() {
        mapOf(
            "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser" to "location_bar_edit_text",
            "com.opera.browser" to "url_field",
            "com.brave.browser" to "url_bar",
            "com.microsoft.emmx" to "url_bar",
            "com.duckduckgo.mobile.android" to "omnibarTextInput",
        ).forEach { (pkg, id) ->
            assertThat(analyzer.analyze(pkg, screen(urlBar(pkg, id, "suggestive-pics.net")), false))
                .isInstanceOf(ScreenDecision.BlockContent::class.java)
        }
    }

    @Test fun `typing an explicit search in the address bar is blocked`() {
        val d = analyzer.analyze("com.android.chrome", screen(urlBar("com.android.chrome", "url_bar", "video sexe gratuite")), false)
        assertThat(d).isEqualTo(ScreenDecision.BlockContent(BlockReason.KEYWORD_TEXT))
    }

    @Test fun `unknown browsers (Phoenix on Tecno) are scanned fully`() {
        val d = analyzer.analyze("com.transsion.phoenix", screen(FakeNode(text = "m.adultsite.com")), false)
        assertThat(d).isInstanceOf(ScreenDecision.BlockContent::class.java)
    }

    @Test fun `in-app browser of Xender and Vidmate`() {
        listOf("cn.xender", "com.nemo.vidmate").forEach { pkg ->
            assertThat(analyzer.analyze(pkg, screen(FakeNode(text = "https://adultsite.com/x")), false))
                .isInstanceOf(ScreenDecision.BlockContent::class.java)
        }
    }

    @Test fun `any app showing a WebView with adult content (in-app browser heuristic)`() {
        val d = analyzer.analyze("com.some.newsapp", screen(FakeNode(text = "Retour"), webView("Bienvenue sur adultsite.com")), false)
        assertThat(d).isInstanceOf(ScreenDecision.BlockContent::class.java)
    }

    @Test fun `private messages outside WebView are not scanned (no false positive in WhatsApp)`() {
        val d = analyzer.analyze("com.whatsapp", screen(FakeNode(text = "regarde adultsite.com mdr")), false)
        assertThat(d).isEqualTo(ScreenDecision.Nothing)
    }

    @Test fun `normal browsing is allowed`() {
        val d = analyzer.analyze("com.android.chrome", screen(urlBar("com.android.chrome", "url_bar", "fr.wikipedia.org/wiki/Douala"), webView("Douala est la capitale économique du Cameroun")), false)
        assertThat(d).isEqualTo(ScreenDecision.Nothing)
    }

    // --- Garde des réglages ---
    @Test fun `protected settings screen when locked`() {
        val s = screen(FakeNode(text = "Rempart"), FakeNode(text = "Désinstaller"), FakeNode(text = "Forcer l'arrêt"))
        assertThat(analyzer.analyze("com.android.settings", s, locked = true)).isEqualTo(ScreenDecision.LeaveProtectedSettings)
        assertThat(analyzer.analyze("com.android.settings", s, locked = false)).isEqualTo(ScreenDecision.Nothing)
    }

    @Test fun `title from the window event is used too`() {
        val d = analyzer.analyze("com.android.settings", screen(FakeNode(text = "Désactivé")), true, listOf("DNS privé"))
        assertThat(d).isEqualTo(ScreenDecision.LeaveProtectedSettings)
    }

    @Test fun `own app is ignored`() {
        assertThat(analyzer.analyze("org.tidalex.rempart", screen(FakeNode(text = "porn")), true)).isEqualTo(ScreenDecision.Nothing)
    }

    // --- Performance / batterie ---
    @Test fun `huge trees are bounded and analysed quickly`() {
        fun deep(n: Int): FakeNode = if (n == 0) FakeNode(text = "fin") else FakeNode(text = "élément $n", children = listOf(deep(n - 1)))
        val wide = FakeNode(className = "android.webkit.WebView", children = (0 until 5_000).map { FakeNode(text = "Paragraphe ordinaire numéro $it") })
        val root = screen(deep(500), wide)
        repeat(20) { analyzer.analyze("com.android.chrome", root, true) }
        val t0 = System.nanoTime()
        repeat(200) { analyzer.analyze("com.android.chrome", root, true) }
        val ms = (System.nanoTime() - t0) / 1e6 / 200
        // Seuil : 10 ms sur PC de développement, marge x3 sur les machines de CI partagées.
        val budget = if (System.getenv("CI") != null) 30.0 else 10.0
        assertThat(ms).isLessThan(budget)
    }

    @Test fun `browser id matching`() {
        assertThat(Browsers.isUrlBarId("com.android.chrome:id/url_bar")).isTrue()
        assertThat(Browsers.isUrlBarId("com.whatsapp:id/conversation")).isFalse()
    }
}
