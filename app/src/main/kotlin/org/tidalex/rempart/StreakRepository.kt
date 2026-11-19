package org.tidalex.rempart

import android.content.Context
import androidx.core.content.edit
import org.tidalex.rempart.core.progress.Streak
import java.time.LocalDate

/** Compteur de jours sans rechute, stocké uniquement sur le téléphone. */
class StreakRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rempart_streak", Context.MODE_PRIVATE)

    fun get(): Streak {
        val start = prefs.getString("start", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now().also { prefs.edit { putString("start", it.toString()) } }
        return Streak(start, prefs.getLong("best", 0), prefs.getInt("relapses", 0))
    }

    fun relapse(): Streak {
        val s = get().relapse(LocalDate.now())
        prefs.edit { putString("start", s.startDate.toString()); putLong("best", s.bestDays); putInt("relapses", s.relapses) }
        return s
    }
}

/** Onboarding terminé ? */
object Onboarding {
    private fun prefs(c: Context) = c.getSharedPreferences("rempart_onboarding", Context.MODE_PRIVATE)
    fun consented(c: Context) = prefs(c).getBoolean("consented", false)
    fun setConsented(c: Context) = prefs(c).edit { putBoolean("consented", true) }
    fun partnerStepDone(c: Context) = prefs(c).getBoolean("partnerDone", false)
    fun setPartnerStepDone(c: Context) = prefs(c).edit { putBoolean("partnerDone", true) }
}
