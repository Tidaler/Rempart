package org.tidalex.rempart.lock

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Battement toutes les 15 min : fait avancer le délai de 72 h, détecte les
 * interruptions de protection et relance ce qui doit l'être (via [onBeat]).
 * Coût batterie négligeable : quelques millisecondes, regroupées par WorkManager.
 */
class HeartbeatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        beat(applicationContext, periodic = true)
        return Result.success()
    }

    companion object {
        /**
         * Action supplémentaire branchée par le module :app : relancer le filtre et,
         * lors d'un battement périodique, noter une interruption s'il était arrêté.
         */
        @Volatile var onBeat: ((Context, Boolean) -> Unit)? = null

        /**
         * Avant, un trou de plus de 45 min entre deux battements était noté comme
         * une interruption. Mais Android retarde les tâches pendant la veille
         * (Doze) : le journal se remplissait de fausses alertes chaque nuit.
         * On constate désormais l'interruption directement (filtre arrêté).
         */
        fun beat(context: Context, periodic: Boolean = false) {
            val repo = LockRepository.get(context)
            repo.tick()
            if (repo.isLocked) DeviceOwnerPolicies.apply(context)
            runCatching { onBeat?.invoke(context, periodic) }
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "heartbeat", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
