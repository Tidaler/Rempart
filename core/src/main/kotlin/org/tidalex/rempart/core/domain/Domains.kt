package org.tidalex.rempart.core.domain

import java.net.IDN

/**
 * Normalisation des noms de domaine : minuscules, sans point final, sans port,
 * conversion IDN (punycode) pour qu'une variante Unicode d'un domaine bloqué
 * ne passe pas à travers le filtre.
 */
object Domains {

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** Retourne le domaine normalisé, ou null s'il n'est pas un nom d'hôte valide. */
    fun normalize(raw: String): String? {
        var host = raw.trim().lowercase()
        if (host.isEmpty()) return null
        // Retire un éventuel schéma, chemin, identifiants ou port.
        host = host.substringAfter("://")
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        host = host.substringAfterLast('@')
        if (host.startsWith("[")) return null // IPv6 littérale : pas un domaine
        host = host.substringBefore(':')
        host = host.trimEnd('.').trimStart('.')
        if (host.isEmpty() || host.length > 253) return null
        if (host.any { it.code > 127 }) {
            host = try {
                IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
            } catch (e: IllegalArgumentException) {
                return null
            }
        }
        if (!host.contains('.')) return null
        if (host.split('.').any { it.isEmpty() || it.length > 63 }) return null
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null
        return host
    }

    fun isIpLiteral(host: String): Boolean = IPV4.matches(host)

    /**
     * Le domaine et tous ses suffixes parents (sans le TLD seul) :
     * "a.b.example.com" -> ["a.b.example.com", "b.example.com", "example.com"].
     */
    fun selfAndParents(host: String): List<String> {
        val parts = host.split('.')
        if (parts.size < 2) return listOf(host)
        return (0..parts.size - 2).map { i -> parts.subList(i, parts.size).joinToString(".") }
    }
}
