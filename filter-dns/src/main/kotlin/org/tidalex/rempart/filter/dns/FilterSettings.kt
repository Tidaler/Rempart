package org.tidalex.rempart.filter.dns

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Réglages du filtre réseau, stockés localement (jamais envoyés nulle part). */
class FilterSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("rempart_filter", Context.MODE_PRIVATE)

    /** L'utilisateur veut que le filtre tourne (relancé au démarrage du téléphone). */
    var wanted: Boolean
        get() = prefs.getBoolean("wanted", false)
        set(v) = prefs.edit { putBoolean("wanted", v) }

    var upstream: UpstreamChoice
        get() = runCatching { UpstreamChoice.valueOf(prefs.getString("upstream", null)!!) }.getOrDefault(UpstreamChoice.DEFAULT)
        set(v) = prefs.edit { putString("upstream", v.name) }

    var safeSearch: Boolean
        get() = prefs.getBoolean("safeSearch", true)
        set(v) = prefs.edit { putBoolean("safeSearch", v) }

    var youtubeRestricted: Boolean
        get() = prefs.getBoolean("youtubeRestricted", true)
        set(v) = prefs.edit { putBoolean("youtubeRestricted", v) }

    var blockedTotal: Long
        get() = prefs.getLong("blockedTotal", 0)
        set(v) = prefs.edit { putLong("blockedTotal", v) }

    var listUpdatedAt: Long
        get() = prefs.getLong("listUpdatedAt", 0)
        set(v) = prefs.edit { putLong("listUpdatedAt", v) }

    var listSize: Int
        get() = prefs.getInt("listSize", 0)
        set(v) = prefs.edit { putInt("listSize", v) }
}
