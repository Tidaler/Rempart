package org.tidalex.rempart.core.domain

import java.io.BufferedReader

/**
 * Liste de blocage compacte : chaque domaine est stocké sous forme d'empreinte
 * 64 bits (FNV-1a) dans un tableau trié. 150 000 domaines tiennent dans ~1,2 Mo
 * (contre ~15 Mo pour un HashSet<String>), et la recherche est une dichotomie.
 *
 * Sémantique « joker » : bloquer `example.com` bloque aussi `www.example.com`.
 * La probabilité de collision 64 bits sur 10^6 entrées est ~ 3·10^-8 : négligeable.
 */
class DomainBlocklist private constructor(private val hashes: LongArray) {

    val size: Int get() = hashes.size

    fun containsExact(normalizedHost: String): Boolean =
        hashes.binarySearch(fnv1a64(normalizedHost)) >= 0

    /** Vrai si l'hôte ou l'un de ses domaines parents est dans la liste. */
    fun matches(host: String): Boolean {
        val normalized = Domains.normalize(host) ?: return false
        return Domains.selfAndParents(normalized).any { containsExact(it) }
    }

    fun toLongArray(): LongArray = hashes.copyOf()

    companion object {
        /**
         * Domaines d'infrastructure à ne jamais bloquer en entier, même si une liste
         * tierce les contient par erreur (cela casserait le téléphone : mises à jour,
         * Play Store, messagerie). Leurs sous-domaines listés restent bloqués.
         */
        val NEVER_BLOCK = setOf(
            "google.com", "googleapis.com", "gstatic.com", "googleusercontent.com", "googlevideo.com",
            "android.com", "gvt1.com", "gvt2.com", "youtube.com", "ytimg.com", "apple.com", "icloud.com",
            "microsoft.com", "live.com", "office.com", "windows.net", "whatsapp.com", "whatsapp.net",
            "facebook.com", "fbcdn.net", "instagram.com", "cdninstagram.com", "wikipedia.org", "wikimedia.org",
            "github.com", "githubusercontent.com", "cloudflare.com", "amazonaws.com", "cloudfront.net",
            "akamaihd.net", "akamaized.net", "fastly.net", "telegram.org", "samsung.com", "xiaomi.com",
        )

        val EMPTY = DomainBlocklist(LongArray(0))

        fun of(domains: Iterable<String>): DomainBlocklist {
            val builder = Builder()
            domains.forEach { builder.add(it) }
            return builder.build()
        }

        fun fromHashes(sorted: LongArray): DomainBlocklist {
            for (i in 1 until sorted.size) require(sorted[i - 1] < sorted[i]) { "hashes non triés" }
            return DomainBlocklist(sorted)
        }

        /**
         * Lit un fichier de liste. Formats acceptés (une entrée par ligne) :
         * `domaine`, `0.0.0.0 domaine` / `127.0.0.1 domaine` (format hosts),
         * `*.domaine`, `||domaine^` (Adblock), commentaires `#` ou `!`.
         */
        fun parse(reader: BufferedReader, builder: Builder = Builder()): Builder {
            reader.forEachLine { line -> parseLine(line)?.let(builder::add) }
            return builder
        }

        fun parseLine(rawLine: String): String? {
            var line = rawLine.substringBefore('#').trim()
            if (line.isEmpty() || line.startsWith("!")) return null
            val parts = line.split(Regex("\\s+"))
            line = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::")) {
                parts[1]
            } else {
                parts[0]
            }
            line = line.removePrefix("||").removeSuffix("^").removePrefix("*.")
            if (line == "localhost" || line == "0.0.0.0" || line.endsWith(".localdomain")) return null
            return Domains.normalize(line)
        }

        /** Empreinte FNV-1a 64 bits d'une chaîne ASCII. */
        fun fnv1a64(s: String): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (c in s) {
                h = h xor (c.code.toLong() and 0xff)
                h *= 0x100000001b3L
            }
            return h
        }
    }

    class Builder {
        private var buf = LongArray(1024)
        private var n = 0

        fun add(domain: String): Builder {
            val normalized = Domains.normalize(domain) ?: return this
            if (normalized in NEVER_BLOCK) return this
            if (n == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[n++] = fnv1a64(normalized)
            return this
        }

        fun addAll(domains: Iterable<String>): Builder = apply { domains.forEach { add(it) } }

        fun build(): DomainBlocklist {
            val arr = buf.copyOf(n)
            arr.sort()
            // Déduplication en place.
            var w = 0
            for (i in arr.indices) if (w == 0 || arr[i] != arr[w - 1]) arr[w++] = arr[i]
            return DomainBlocklist(arr.copyOf(w))
        }
    }
}
