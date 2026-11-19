package org.tidalex.rempart.core.domain

/**
 * Recherche sécurisée imposée par DNS : les moteurs publient des noms d'hôte
 * qui forcent le mode strict. On répond à `www.google.fr` avec les adresses de
 * `forcesafesearch.google.com`, etc. Méthode documentée par Google, Microsoft,
 * YouTube et DuckDuckGo pour les réseaux d'écoles et d'entreprises.
 */
object SafeSearch {

    const val GOOGLE = "forcesafesearch.google.com"
    const val BING = "strict.bing.com"
    const val YOUTUBE = "restrict.youtube.com"
    const val DUCKDUCKGO = "safe.duckduckgo.com"
    const val PIXABAY = "safesearch.pixabay.com"

    private val GOOGLE_HOST = Regex("""^(www\.)?google\.(com|[a-z]{2,3}|co\.[a-z]{2}|com\.[a-z]{2})$""")

    private val YOUTUBE_HOSTS = setOf(
        "www.youtube.com", "m.youtube.com", "youtube.com", "youtubei.googleapis.com",
        "youtube.googleapis.com", "www.youtube-nocookie.com",
    )

    /** Hôte de remplacement à résoudre à la place de [host], ou null si aucun. */
    fun rewriteTarget(host: String, youtubeRestricted: Boolean = true): String? {
        val h = Domains.normalize(host) ?: return null
        return when {
            GOOGLE_HOST.matches(h) -> GOOGLE
            h == "www.bing.com" || h == "bing.com" -> BING
            h == "duckduckgo.com" || h == "www.duckduckgo.com" || h == "start.duckduckgo.com" -> DUCKDUCKGO
            h == "pixabay.com" || h == "www.pixabay.com" -> PIXABAY
            youtubeRestricted && h in YOUTUBE_HOSTS -> YOUTUBE
            else -> null
        }
    }
}
