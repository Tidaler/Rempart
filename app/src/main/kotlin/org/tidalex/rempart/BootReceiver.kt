package org.tidalex.rempart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.lock.HeartbeatWorker
import org.tidalex.rempart.lock.LockRepository

/**
 * Démarrage du téléphone / mise à jour de l'app : relance le filtre et enregistre
 * l'éventuelle interruption. Changement d'heure : simplement journalisé (le délai
 * de 72 h ne dépend pas de l'heure, voir MonotonicCountdown).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RempartApp.wire(context)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED -> {
                val repo = LockRepository.get(context)
                if (repo.isLocked) repo.record(IntegrityEvent.CLOCK_CHANGED)
            }
            Intent.ACTION_SHUTDOWN -> LockRepository.get(context).tick() // dernier point de contrôle du délai
            else -> {
                HeartbeatWorker.beat(context)
                Protection.ensureVpnRunning(context)
            }
        }
    }
}
