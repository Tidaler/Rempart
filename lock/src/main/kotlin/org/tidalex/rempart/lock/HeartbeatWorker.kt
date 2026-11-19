package org.tidalex.rempart.lock

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.tidalex.rempart.core.integrity.HeartbeatGap
import org.tidalex.rempart.core.integrity.IntegrityEvent
import java.util.concurrent.TimeUnit

/**
 * Battement toutes les 15 min : fait avancer le délai de 72 h, détecte les
 * interruptions de protection et relance ce qui doit l'être (via [onBeat]).
 * Coût batterie négligeable : quelques millisecondes, regroupées par WorkManager.
 */
class HeartbeatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        beat(applicationContext)
        return Result.success()
    }

    companion object {
        private const val MAX_GAP_MS = 45L * 60_000
        /** Action supplémentaire (relancer le VPN…) branchée par le module :app. */
        @Volatile var onBeat: ((Context) -> Unit)? = null

        fun beat(context: Context) {
            val repo = LockRepository.get(context)
            repo.tick()
            val prefs = context.getSharedPreferences("rempart_heartbeat", Context.MODE_PRIVATE)
            val now = SystemClock.elapsedRealtime()
            val prev = prefs.getLong("last", -1)
            if (prev >= 0 && repo.isLocked && HeartbeatGap.isGap(prev, now, MAX_GAP_MS)) {
                repo.record(IntegrityEvent.PROTECTION_GAP)
            }
            prefs.edit().putLong("last", now).apply()
            if (repo.isLocked) DeviceOwnerPolicies.apply(context)
            runCatching { onBeat?.invoke(context) }
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "heartbeat", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
