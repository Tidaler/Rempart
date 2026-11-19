package org.tidalex.rempart.filter.accessibility

import org.tidalex.rempart.core.domain.BlockReason
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.Verdict
import org.tidalex.rempart.core.guard.GuardAction
import org.tidalex.rempart.core.guard.ScreenSnapshot
import org.tidalex.rempart.core.guard.SettingsGuard

/** Vue minimale d'un nœud d'accessibilité : permet de tester l'analyse sans Android. */
interface NodeView {
    val text: CharSequence?
    val contentDescription: CharSequence?
    val viewId: String?
    val className: CharSequence?
    val childCount: Int
    fun child(i: Int): NodeView?
}

sealed interface ScreenDecision {
    data object Nothing : ScreenDecision
    data class BlockContent(val reason: BlockReason) : ScreenDecision
    data object LeaveProtectedSettings : ScreenDecision
}

/**
 * Analyse d'une fenêtre. Budget strict (nœuds, profondeur, texte) pour rester
 * économe en batterie : la plupart des écrans sont traités en < 5 ms.
 */
class ScreenAnalyzer(
    private val policyProvider: () -> ContentPolicy,
    private val guard: SettingsGuard = SettingsGuard(),
    private val maxNodes: Int = 600,
    private val maxDepth: Int = 40,
) {
    fun analyze(packageName: String, root: NodeView?, locked: Boolean, eventTexts: List<CharSequence> = emptyList()): ScreenDecision {
        if (root == null) return ScreenDecision.Nothing
        if (packageName == OWN_PACKAGE) return ScreenDecision.Nothing

        val collected = Collected()
        collect(root, 0, insideWebView = false, collected)
        eventTexts.forEach { collected.all += it.toString() }

        // 1. Garde des Réglages (seulement verrou actif).
        if (locked && guard.evaluate(ScreenSnapshot(packageName, null, collected.all), locked) == GuardAction.LEAVE_SCREEN) {
            return ScreenDecision.LeaveProtectedSettings
        }

        val policy = policyProvider()
        // 2. Navigateurs : la barre d'adresse fait foi (fonctionne aussi en navigation privée).
        val browserIds = Browsers.URL_BAR_IDS[packageName]
        if (browserIds != null || packageName in Browsers.EXTRA_BROWSERS) {
            collected.urlBars.forEach { url -> verdict(policy.checkText(url))?.let { return it } }
            // Titre et contenu de page : seulement les noms de domaine et recherches explicites.
            return verdict(policy.checkText(collected.all.joinToString(" \n ").take(MAX_TEXT))) ?: ScreenDecision.Nothing
        }
        // 3. Applis connues pour intégrer un navigateur ou des vidéos (Xender, Vidmate…) : tout le texte.
        if (packageName in Browsers.IN_APP_BROWSER_APPS) {
            return verdict(policy.checkText(collected.all.joinToString(" \n ").take(MAX_TEXT))) ?: ScreenDecision.Nothing
        }
        // 4. Toute autre appli : seulement le contenu de ses WebView (navigateurs intégrés),
        //    pour ne pas réagir aux messages privés d'une messagerie.
        if (collected.webViewTexts.isNotEmpty()) {
            return verdict(policy.checkText(collected.webViewTexts.joinToString(" \n ").take(MAX_TEXT))) ?: ScreenDecision.Nothing
        }
        return ScreenDecision.Nothing
    }

    private fun verdict(v: Verdict): ScreenDecision? =
        (v as? Verdict.Block)?.let { if (it.reason == BlockReason.DNS_BYPASS) null else ScreenDecision.BlockContent(it.reason) }

    private class Collected {
        val all = ArrayList<String>()
        val urlBars = ArrayList<String>()
        val webViewTexts = ArrayList<String>()
        var count = 0
    }

    private fun collect(node: NodeView, depth: Int, insideWebView: Boolean, c: Collected) {
        if (c.count++ >= maxNodes || depth > maxDepth) return
        val inWeb = insideWebView || node.className?.toString() == "android.webkit.WebView"
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        for (t in listOfNotNull(text, desc)) {
            c.all += t
            if (inWeb) c.webViewTexts += t
        }
        val id = node.viewId
        if (text != null && id != null && Browsers.isUrlBarId(id)) c.urlBars += text
        for (i in 0 until node.childCount) node.child(i)?.let { collect(it, depth + 1, inWeb, c) }
    }

    companion object {
        const val OWN_PACKAGE = "org.tidalex.rempart"
        private const val MAX_TEXT = 20_000
    }
}

/** Navigateurs connus et identifiants de leur barre d'adresse. */
object Browsers {
    val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.opera.gx" to listOf("url_field"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.yandex.browser" to listOf("bro_omnibar_address_title_text"),
        "com.mi.globalbrowser" to listOf("url"),
        "com.android.browser" to listOf("url"),
        "com.UCMobile.intl" to listOf("titlebar"),
    )

    /** Navigateurs dont l'identifiant de barre d'adresse est inconnu : analyse de tout le texte. */
    val EXTRA_BROWSERS = setOf(
        "com.transsion.phoenix", "com.cloudmosa.puffinFree", "com.ecosia.android", "org.torproject.torbrowser",
        "com.hsv.freeadblockerbrowser", "mark.via.gp", "com.qwant.liberty", "com.huawei.browser",
        "com.heytap.browser", "com.vivo.browser", "com.sec.android.app.sbrowser.beta", "com.aloha.browser",
    )

    /** Applis avec navigateur ou vidéos intégrés, analysées en entier. */
    val IN_APP_BROWSER_APPS = setOf(
        "cn.xender", "com.nemo.vidmate", "com.snaptube.premium", "com.dywx.larkplayer", "com.lenovo.anyshare.gps",
        "video.downloader.videodownloader", "com.tencent.mtt", "com.uc.browser.en", "free.vpn.unblock.proxy.turbovpn",
    )

    private val ALL_IDS = URL_BAR_IDS.values.flatten().toSet()
    fun isUrlBarId(fullId: String): Boolean = fullId.substringAfter(":id/") in ALL_IDS
}
