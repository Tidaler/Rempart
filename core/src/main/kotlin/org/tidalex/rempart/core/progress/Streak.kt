package org.tidalex.rempart.core.progress

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Compteur de jours sans rechute. Basé sur la date du calendrier : tricher
 * avec l'heure ne tromperait que soi-même, et cela ne déverrouille rien.
 */
data class Streak(
    val startDate: LocalDate,
    val bestDays: Long = 0,
    val relapses: Int = 0,
) {
    fun currentDays(today: LocalDate): Long = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0)

    fun relapse(today: LocalDate): Streak =
        Streak(startDate = today, bestDays = maxOf(bestDays, currentDays(today)), relapses = relapses + 1)

    fun best(today: LocalDate): Long = maxOf(bestDays, currentDays(today))

    /** Prochain palier symbolique à atteindre. */
    fun nextMilestone(today: LocalDate): Long =
        MILESTONES.firstOrNull { it > currentDays(today) } ?: ((currentDays(today) / 365) + 1) * 365

    companion object {
        val MILESTONES = listOf(1L, 3, 7, 14, 21, 30, 60, 90, 180, 365)
    }
}
