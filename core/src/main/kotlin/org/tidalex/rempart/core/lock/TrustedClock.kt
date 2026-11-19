package org.tidalex.rempart.core.lock

/**
 * Horloge de confiance. Sur Android : `SystemClock.elapsedRealtime()` — le temps
 * écoulé depuis le démarrage, que l'utilisateur ne peut PAS modifier (contrairement
 * à l'heure système). C'est la base du délai de 72 h.
 */
fun interface TrustedClock {
    fun elapsedRealtimeMs(): Long
}

/**
 * Compte à rebours qui n'avance qu'avec du temps réellement mesuré par l'horloge
 * monotone. Propriété de sécurité : il ne peut JAMAIS compter plus de temps qu'il
 * ne s'en est réellement écoulé.
 *
 * - Même démarrage (now >= last) : on ajoute exactement now - last.
 * - Redémarrage détecté (now < last) : on ajoute `now`, le temps depuis le
 *   démarrage, mais pas le temps éteint ni celui entre le dernier point de
 *   contrôle et l'arrêt (sous-estimation volontaire, donc sûre).
 * - Changer l'heure ou le fuseau n'a aucun effet.
 */
data class MonotonicCountdown(
    val requiredMs: Long,
    val accumulatedMs: Long = 0,
    val lastElapsedMs: Long,
) {
    val remainingMs: Long get() = (requiredMs - accumulatedMs).coerceAtLeast(0)
    val isComplete: Boolean get() = accumulatedMs >= requiredMs
    val progress: Float get() = if (requiredMs == 0L) 1f else (accumulatedMs.toFloat() / requiredMs).coerceIn(0f, 1f)

    fun advance(clock: TrustedClock): MonotonicCountdown {
        val now = clock.elapsedRealtimeMs()
        val added = if (now >= lastElapsedMs) now - lastElapsedMs else now.coerceAtLeast(0)
        return copy(accumulatedMs = (accumulatedMs + added).coerceAtMost(requiredMs), lastElapsedMs = now)
    }

    companion object {
        fun start(requiredMs: Long, clock: TrustedClock) =
            MonotonicCountdown(requiredMs = requiredMs, accumulatedMs = 0, lastElapsedMs = clock.elapsedRealtimeMs())
    }
}
