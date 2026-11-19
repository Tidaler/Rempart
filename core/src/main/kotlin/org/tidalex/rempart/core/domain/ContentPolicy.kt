package org.tidalex.rempart.core.domain

/** Raison d'un blocage, utilisée pour l'écran d'encouragement et les statistiques (jamais le domaine). */
enum class BlockReason { BLOCKLIST, KEYWORD_HOST, KEYWORD_TEXT, PROTECTED_SETTINGS, DNS_BYPASS }

sealed interface Verdict {
    data object Allow : Verdict
    data class Block(val reason: BlockReason) : Verdict
}

/**
 * Décision centrale « ce domaine / ce texte est-il interdit ? », partagée par
 * le filtre DNS et le service d'accessibilité.
 */
class ContentPolicy(
    @Volatile var blocklist: DomainBlocklist,
    @Volatile var bypassList: DomainBlocklist = DomainBlocklist.EMPTY,
) {
    fun checkHost(host: String): Verdict {
        val h = Domains.normalize(host) ?: return Verdict.Allow
        if (blocklist.matches(h)) return Verdict.Block(BlockReason.BLOCKLIST)
        if (KeywordRules.hostLooksAdult(h)) return Verdict.Block(BlockReason.KEYWORD_HOST)
        if (bypassList.matches(h)) return Verdict.Block(BlockReason.DNS_BYPASS)
        return Verdict.Allow
    }

    /** Vérifie un texte visible (barre d'adresse, titre, recherche, page web). */
    fun checkText(text: String): Verdict {
        for (host in UrlExtractor.hosts(text)) {
            val v = checkHost(host)
            if (v is Verdict.Block && v.reason != BlockReason.DNS_BYPASS) return v
        }
        if (KeywordRules.textLooksAdult(text)) return Verdict.Block(BlockReason.KEYWORD_TEXT)
        return Verdict.Allow
    }
}

/** Extraction des noms d'hôte présents dans un texte libre (barre d'URL, page). */
object UrlExtractor {
    // schéma optionnel, hôte avec au moins un point et un TLD alphabétique de 2 à 24 caractères.
    private val HOST = Regex(
        """(?i)(?:[a-z][a-z0-9+.-]*://)?((?:[a-z0-9¡-￿](?:[a-z0-9¡-￿-]{0,61}[a-z0-9¡-￿])?\.)+(?:[a-z¡-￿]{2,24}|xn--[a-z0-9-]{1,59}))(?::\d{1,5})?(?=$|[/?#\s:),;"'>\]])""",
    )

    fun hosts(text: String, limit: Int = 32): List<String> {
        if (text.length > 20_000) return hosts(text.take(20_000), limit)
        return HOST.findAll(text).mapNotNull { Domains.normalize(it.groupValues[1]) }
            .filterNot { looksLikeFileName(it) }
            .distinct().take(limit).toList()
    }

    private val FILE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "mp3", "pdf", "txt", "apk", "zip", "html", "php", "js", "css", "kt", "java")
    private fun looksLikeFileName(h: String) = h.substringAfterLast('.') in FILE_EXT
}
