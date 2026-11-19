package org.tidalex.rempart.core.lock

/**
 * Sérialisation simple clé/valeur de [LockState] (pas de dépendance JSON).
 * Le module :lock la persiste dans un stockage privé de l'app.
 */
object LockStateCodec {

    fun encode(s: LockState): Map<String, String> = buildMap {
        put("phase", s.phase.name)
        put("delayHours", s.delayHours.toString())
        put("failedAttempts", s.failedAttempts.toString())
        s.partner?.let { put("partner", it.serialize()) }
        s.partnerName?.let { put("partnerName", it) }
        s.partnerPhone?.let { put("partnerPhone", it) }
        s.release?.let { put("release", encodeCountdown(it)) }
        s.attemptCooldown?.let { put("cooldown", encodeCountdown(it)) }
    }

    /**
     * Décodage défensif : une donnée corrompue ou incohérente ne doit jamais
     * aboutir à un déverrouillage. En cas de doute, on reste verrouillé.
     */
    fun decode(m: Map<String, String>): LockState {
        val phase = m["phase"]?.let { runCatching { Phase.valueOf(it) }.getOrNull() }
        val release = m["release"]?.let(::decodeCountdown)
        val delay = m["delayHours"]?.toIntOrNull()?.takeIf { it in 24..168 } ?: LockState.DEFAULT_DELAY_HOURS
        val resolvedPhase = when {
            phase == null && m.isEmpty() -> Phase.OPEN
            phase == null -> Phase.LOCKED // données présentes mais phase illisible : on verrouille
            phase == Phase.RELEASING && release == null -> Phase.LOCKED
            else -> phase
        }
        return LockState(
            phase = resolvedPhase,
            partner = m["partner"]?.let(PartnerSecret::deserialize),
            partnerName = m["partnerName"],
            partnerPhone = m["partnerPhone"],
            release = if (resolvedPhase == Phase.RELEASING) release else null,
            failedAttempts = m["failedAttempts"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            attemptCooldown = m["cooldown"]?.let(::decodeCountdown),
            delayHours = delay,
        )
    }

    private fun encodeCountdown(c: MonotonicCountdown) = "${c.requiredMs},${c.accumulatedMs},${c.lastElapsedMs}"

    private fun decodeCountdown(s: String): MonotonicCountdown? {
        val p = s.split(',').map { it.toLongOrNull() ?: return null }
        if (p.size != 3 || p[0] <= 0 || p[1] < 0 || p[1] > p[0]) return null
        return MonotonicCountdown(p[0], p[1], p[2])
    }
}
