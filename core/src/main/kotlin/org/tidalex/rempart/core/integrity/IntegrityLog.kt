package org.tidalex.rempart.core.integrity

/**
 * Journal d'intégrité, visible par l'utilisateur et son partenaire dans l'app.
 * Il ne contient AUCUN site visité : seulement des événements de protection.
 */
/** Les libellés sont traduits par l'app (ressources Android), selon la langue du téléphone. */
enum class IntegrityEvent {
    LOCK_ENGAGED,
    RELEASE_REQUESTED,
    RELEASE_CANCELLED,
    RELEASE_COMPLETED,
    PARTNER_UNLOCK,
    PARTNER_CODE_FAILED,
    VPN_REVOKED,
    VPN_STARTED,
    ACCESSIBILITY_LOST,
    ADMIN_DISABLE_REQUESTED,
    PROTECTED_SCREEN_BLOCKED,
    CLOCK_CHANGED,
    PROTECTION_GAP,
}

data class IntegrityEntry(val wallTimeMs: Long, val event: IntegrityEvent)

object IntegrityLogCodec {
    const val MAX_ENTRIES = 200

    fun append(existing: List<IntegrityEntry>, entry: IntegrityEntry): List<IntegrityEntry> =
        (existing + entry).takeLast(MAX_ENTRIES)

    fun encode(entries: List<IntegrityEntry>): String = entries.joinToString("\n") { "${it.wallTimeMs};${it.event.name}" }

    fun decode(s: String): List<IntegrityEntry> = s.lineSequence().mapNotNull { line ->
        val p = line.split(';')
        val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val e = p.getOrNull(1)?.let { runCatching { IntegrityEvent.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
        IntegrityEntry(t, e)
    }.toList()
}

/**
 * Détecte les trous de protection grâce à des « battements » périodiques :
 * si l'écart entre deux battements dépasse [maxGapMs] (en temps monotone ou
 * via un redémarrage), on enregistre une interruption.
 */
object HeartbeatGap {
    fun isGap(previousElapsedMs: Long, nowElapsedMs: Long, maxGapMs: Long): Boolean =
        nowElapsedMs < previousElapsedMs || nowElapsedMs - previousElapsedMs > maxGapMs
}
