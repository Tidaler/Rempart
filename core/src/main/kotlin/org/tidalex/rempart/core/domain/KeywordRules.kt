package org.tidalex.rempart.core.domain

/**
 * Règles par mots-clés, en complément des listes : elles attrapent les sites
 * récents qui ne sont pas encore listés et les recherches explicites.
 *
 * Les mots sont choisis pour éviter les faux positifs classiques
 * (« Essex », « Sussex », « analyse », « therapist »…) : on ne bloque pas « sex »
 * seul dans un domaine, seulement des termes sans ambiguïté.
 */
object KeywordRules {

    /** Termes qui, présents n'importe où dans un nom d'hôte, suffisent à bloquer. */
    private val HOST_TERMS = listOf(
        "porn", "xxx", "xvideo", "xnxx", "xhamster", "redtube", "youporn", "pornhub",
        "hentai", "rule34", "brazzers", "chaturbate", "stripchat", "onlyfans", "fansly",
        "camsoda", "bongacams", "livejasmin", "spankbang", "eporner", "motherless",
        "nudes", "nudist", "naked", "erotic", "erotique", "camgirl", "sexcam", "sexchat",
        "sextape", "milf", "nsfw", "fapello", "thothub", "jav-", "javhd", "hqporner",
    )

    /** Mots entiers (séparés par des non-lettres) bloquant dans un nom d'hôte. */
    private val HOST_WORDS = setOf("sex", "sexy", "porno", "nude", "boobs", "tits", "lingerie", "bikini", "escort", "xxx")

    /** Exceptions : domaines légitimes contenant un terme ci-dessus. */
    private val HOST_ALLOW_SUFFIXES = listOf(
        "essex.ac.uk", "sussex.ac.uk", "middlesex.ac.uk", "wikipedia.org",
        "sexualhealth.org", "sexualites-info-sante.fr", "onsexprime.fr",
    )

    /** Expressions de recherche explicites (multilingue FR/EN/ES/PT). */
    private val QUERY_TERMS = listOf(
        "porn", "porno", "xxx", "xvideos", "xnxx", "xhamster", "pornhub", "hentai", "rule34",
        "nude", "nudes", "naked", "nue ", "nu intégral", "seins nus", "topless", "sextape",
        "sexe ", "sex video", "sex tape", "video sexe", "vidéo sexe", "film x", "onlyfans",
        "nsfw", "milf", "camgirl", "desnuda", "pelada", "gostosa", "culona",
        "baise", "lingerie sexy", "bikini sexy", "sexy girl", "sexy girls", "hot girl", "hot girls",
        "big boobs", "boobs", "twerk", "strip tease", "striptease", "femme nue", "fille nue",
    )

    private fun words(s: String) = s.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    fun hostLooksAdult(host: String): Boolean {
        val h = Domains.normalize(host) ?: return false
        if (HOST_ALLOW_SUFFIXES.any { h == it || h.endsWith(".$it") }) return false
        if (HOST_TERMS.any { h.contains(it) }) return true
        return words(h).any { it in HOST_WORDS }
    }

    /** Une seule expression compilée : mot entier, insensible à la casse. */
    private val QUERY_REGEX = Regex(
        "(?<![\\p{L}0-9])(?:" + QUERY_TERMS.map { Regex.escape(it.trim()) }.sortedByDescending { it.length }.joinToString("|") + ")(?![\\p{L}0-9])",
        RegexOption.IGNORE_CASE,
    )

    /** Vrai si le texte d'une recherche ou d'un titre de page est explicitement adulte. */
    fun textLooksAdult(text: String): Boolean =
        QUERY_REGEX.containsMatchIn(WHITESPACE.replace(text, " ").lowercase())

    private val WHITESPACE = Regex("""\s+""")
}
