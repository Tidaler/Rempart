package org.tidalex.rempart

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.filter.accessibility.RempartAccessibilityService
import org.tidalex.rempart.filter.dns.BlocklistUpdateWorker
import org.tidalex.rempart.filter.dns.FilterSettings
import org.tidalex.rempart.filter.dns.RempartVpnService
import org.tidalex.rempart.filter.dns.SharedPolicy
import org.tidalex.rempart.lock.HeartbeatWorker
import org.tidalex.rempart.lock.LockRepository

/** Point d'assemblage : relie les modules indépendants entre eux. */
class RempartApp : Application() {

    override fun onCreate() {
        super.onCreate()
        wire(this)
        HeartbeatWorker.schedule(this)
        BlocklistUpdateWorker.schedule(this)
    }

    companion object {
        fun wire(app: Context) {
            val ctx = app.applicationContext
            RempartAccessibilityService.Hooks.policy = { SharedPolicy.get(it) }
            RempartAccessibilityService.Hooks.onBlocked = { c, _ -> Stats.increment(c) }
            RempartVpnService.Events.listener = { type ->
                val repo = LockRepository.get(ctx)
                when (type) {
                    RempartVpnService.Events.Type.REVOKED -> if (repo.isLocked) repo.record(IntegrityEvent.VPN_REVOKED)
                    RempartVpnService.Events.Type.STARTED -> Unit
                }
            }
            HeartbeatWorker.onBeat = { c, periodic ->
                val repo = LockRepository.get(c)
                if (periodic && repo.isLocked && !RempartVpnService.running.value) repo.record(IntegrityEvent.PROTECTION_GAP)
                Protection.ensureVpnRunning(c)
            }
        }
    }
}

/** Compteur de contenus bloqués par l'accessibilité (le VPN tient le sien). */
object Stats {
    private fun prefs(c: Context) = c.getSharedPreferences("rempart_stats", Context.MODE_PRIVATE)
    fun increment(c: Context) = prefs(c).edit { putLong("a11yBlocked", prefs(c).getLong("a11yBlocked", 0) + 1) }
    fun total(c: Context): Long = prefs(c).getLong("a11yBlocked", 0) + FilterSettings(c).blockedTotal
}
