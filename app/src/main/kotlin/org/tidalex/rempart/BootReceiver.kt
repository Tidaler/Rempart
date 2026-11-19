package org.tidalex.rempart

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.lock.HeartbeatWorker
import org.tidalex.rempart.lock.LockRepository

/**
 * Démarrage du téléphone / mise à jour de l'app : relance le filtre. Après une
 * mise à jour, prévient s'il manque une autorisation. Changement d'heure :
 * simplement journalisé (le délai ne dépend pas de l'heure, voir MonotonicCountdown).
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
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                HeartbeatWorker.beat(context)
                Protection.ensureVpnRunning(context)
                if (Onboarding.consented(context)) remindMissingPermissions(context)
            }
            else -> { // démarrage normal ou « démarrage rapide » de certains constructeurs
                HeartbeatWorker.beat(context)
                Protection.ensureVpnRunning(context)
            }
        }
    }

    private fun remindMissingPermissions(context: Context) {
        val missing = Permissions.missing(context).filter { it != PermissionStep.NOTIFICATIONS }
        if (missing.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.permissions_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, 3, open, PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(context.getString(R.string.permissions_notif_title))
                .setContentText(context.getString(R.string.permissions_notif_text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL = "permissions"
        const val NOTIF_ID = 3
    }
}
