package org.tidalex.rempart.core.lock

/**
 * État du verrou.
 *
 * - [Phase.OPEN]     : protection non verrouillée. On peut la configurer, la couper, désinstaller.
 * - [Phase.LOCKED]   : verrouillée. Seules sorties : code partenaire ou demande de délai.
 * - [Phase.RELEASING]: une demande de déverrouillage court (72 h de temps réel, annulable).
 */
enum class Phase { OPEN, LOCKED, RELEASING }

data class LockState(
    val phase: Phase = Phase.OPEN,
    val partner: PartnerSecret? = null,
    val partnerName: String? = null,
    val partnerPhone: String? = null,
    val release: MonotonicCountdown? = null,
    val failedAttempts: Int = 0,
    val attemptCooldown: MonotonicCountdown? = null,
    val delayHours: Int = DEFAULT_DELAY_HOURS,
) {
    val isLocked: Boolean get() = phase != Phase.OPEN

    companion object {
        const val DEFAULT_DELAY_HOURS = 72
        const val FREE_ATTEMPTS = 5
        const val MAX_COOLDOWN_MS = 24L * 3600_000
    }
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongCode(val attemptsBeforeCooldown: Int) : UnlockResult
    data class CoolingDown(val remainingMs: Long) : UnlockResult
    data object NoPartner : UnlockResult
    data object NotLocked : UnlockResult
}

/**
 * Machine à états du verrou, en logique pure, testée hors Android.
 * Chaque fonction renvoie le nouvel état ; la persistance est faite par le module :lock.
 */
class LockEngine(private val clock: TrustedClock) {

    fun engage(state: LockState): LockState {
        check(state.phase == Phase.OPEN) { "déjà verrouillé" }
        return state.copy(phase = Phase.LOCKED, release = null, failedAttempts = 0, attemptCooldown = null)
    }

    /** Le partenaire ne peut être changé que si la protection est ouverte. */
    fun setPartner(state: LockState, code: String?, name: String?, phone: String?): LockState {
        check(state.phase == Phase.OPEN) { "impossible de changer de partenaire pendant le verrouillage" }
        return state.copy(
            partner = code?.let { PartnerSecret.create(it) },
            partnerName = name?.trim()?.ifEmpty { null },
            partnerPhone = phone?.trim()?.ifEmpty { null },
        )
    }

    /** Le délai ne peut être changé que protection ouverte, et jamais sous 24 h. */
    fun setDelayHours(state: LockState, hours: Int): LockState {
        check(state.phase == Phase.OPEN) { "impossible de changer le délai pendant le verrouillage" }
        require(hours in 24..168) { "délai entre 24 h et 7 jours" }
        return state.copy(delayHours = hours)
    }

    fun requestRelease(state: LockState): LockState {
        check(state.phase == Phase.LOCKED) { "aucun verrou actif" }
        return state.copy(phase = Phase.RELEASING, release = MonotonicCountdown.start(state.delayHours * 3600_000L, clock))
    }

    fun cancelRelease(state: LockState): LockState {
        if (state.phase != Phase.RELEASING) return state
        return state.copy(phase = Phase.LOCKED, release = null)
    }

    /** À appeler régulièrement (démarrage, écran ouvert, tâche périodique). */
    fun tick(state: LockState): LockState {
        var s = state
        s.attemptCooldown?.let { c ->
            val adv = c.advance(clock)
            s = s.copy(attemptCooldown = if (adv.isComplete) null else adv)
        }
        if (s.phase == Phase.RELEASING) {
            val adv = s.release!!.advance(clock)
            s = if (adv.isComplete) s.copy(phase = Phase.OPEN, release = null) else s.copy(release = adv)
        }
        return s
    }

    fun unlockWithPartnerCode(state: LockState, code: String): Pair<LockState, UnlockResult> {
        if (!state.isLocked) return state to UnlockResult.NotLocked
        val partner = state.partner ?: return state to UnlockResult.NoPartner
        val s = tick(state)
        s.attemptCooldown?.let { return s to UnlockResult.CoolingDown(it.remainingMs) }
        if (partner.matches(code)) {
            return s.copy(phase = Phase.OPEN, release = null, failedAttempts = 0, attemptCooldown = null) to UnlockResult.Success
        }
        val failures = s.failedAttempts + 1
        val cooldown = cooldownFor(failures)?.let { MonotonicCountdown.start(it, clock) }
        val next = s.copy(failedAttempts = failures, attemptCooldown = cooldown)
        return next to (if (cooldown != null) UnlockResult.CoolingDown(cooldown.remainingMs)
        else UnlockResult.WrongCode(LockState.FREE_ATTEMPTS - failures))
    }

    companion object {
        /** 5 essais libres, puis 1 min, 2 min, 4 min… plafonné à 24 h. */
        fun cooldownFor(failures: Int): Long? {
            if (failures < LockState.FREE_ATTEMPTS) return null
            val exp = (failures - LockState.FREE_ATTEMPTS).coerceAtMost(20)
            return (60_000L shl exp).coerceAtMost(LockState.MAX_COOLDOWN_MS)
        }
    }
}
