package org.tidalex.rempart.core.integrity

/**
 * Journal d'intégrité, visible par l'utilisateur et son partenaire dans l'app.
 * Il ne contient AUCUN site visité : seulement des événements de protection.
 */
enum class IntegrityEvent(val label: String) {
    LOCK_ENGAGED("Verrou activé"),
    RELEASE_REQUESTED("Demande de déverrouillage (délai lancé)"),
    RELEASE_CANCELLED("Demande de déverrouillage annulée"),
    RELEASE_COMPLETED("Délai écoulé : protection déverrouillée"),
    PARTNER_UNLOCK("Déverrouillé avec le code du partenaire"),
    PARTNER_CODE_FAILED("Code partenaire erroné"),
    VPN_REVOKED("Filtre réseau coupé (autre VPN ou désactivation)"),
    VPN_STARTED("Filtre réseau démarré"),
    ACCESSIBILITY_LOST("Surveillance des applis interrompue"),
    ADMIN_DISABLE_REQUESTED("Tentative de retrait de l'administrateur"),
    PROTECTED_SCREEN_BLOCKED("Écran de réglage protégé refusé"),
    CLOCK_CHANGED("Heure du téléphone modifiée"),
    PROTECTION_GAP("Protection interrompue (téléphone éteint, mode sans échec ou arrêt forcé)"),
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
