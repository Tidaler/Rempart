package org.tidalex.rempart.filter.dns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * VPN local : AUCUN trafic ne quitte le téléphone vers un serveur de Rempart.
 * Seules les requêtes DNS (et les résolveurs de contournement connus) passent
 * par l'interface virtuelle ; tout le reste du trafic va directement à Internet.
 */
class RempartVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val pool = Executors.newFixedThreadPool(4)
    private var engine: DnsFilterEngine? = null
    private val policy = ContentPolicy(DomainBlocklist.EMPTY)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); stopSelf(); return START_NOT_STICKY }
            ACTION_RELOAD -> { reload(); return START_STICKY }
        }
        startInForeground()
        if (worker?.isAlive != true) {
            worker = Thread(::run, "rempart-vpn").also { it.start() }
        }
        return START_STICKY
    }

    private fun run() {
        try {
            reload()
            val settings = FilterSettings(this)
            val eng = DnsFilterEngine(
                policy = policy,
                upstream = UdpUpstream.of(settings.upstream) { protect(it) },
                safeSearch = settings.safeSearch,
                youtubeRestricted = settings.youtubeRestricted,
            )
            engine = eng
            val pfd = establish() ?: run { Log.w(TAG, "consentement VPN absent"); stopSelf(); return }
            tun = pfd
            _running.value = true
            Events.emit(Events.Type.STARTED)
            loop(pfd, eng)
        } catch (e: Exception) {
            Log.e(TAG, "arrêt du filtre", e)
        } finally {
            _running.value = false
            engine?.let { FilterSettings(this).blockedTotal += it.blockedCount.getAndSet(0) }
        }
    }

    private fun establish(): ParcelFileDescriptor? {
        val b = Builder()
            .setSession("Rempart")
            .setMtu(1500)
            .addAddress(TUN_ADDRESS, 30)
            .addDnsServer(TUN_DNS)
            .addRoute(TUN_DNS, 32)
            .setBlocking(true)
        BypassResolvers.IPV4.forEach { b.addRoute(it, 32) }
        runCatching {
            b.addAddress(TUN_ADDRESS6, 128)
            BypassResolvers.IPV6.forEach { b.addRoute(it, 128) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) b.setMetered(false)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) b.setConfigureIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        return b.establish()
    }

    private fun loop(pfd: ParcelFileDescriptor, eng: DnsFilterEngine) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32_767)
        var lastFlush = System.currentTimeMillis()
        while (!Thread.currentThread().isInterrupted) {
            val n = try { input.read(buf) } catch (e: IOException) { break }
            if (n <= 0) continue
            val packet = buf.copyOf(n)
            pool.execute {
                val reply = runCatching { eng.handlePacket(packet, packet.size) }.getOrNull() ?: return@execute
                synchronized(output) { runCatching { output.write(reply) } }
            }
            val now = System.currentTimeMillis()
            if (now - lastFlush > 60_000) { // statistique agrégée, jamais le détail des sites
                FilterSettings(this).blockedTotal += eng.blockedCount.getAndSet(0)
                lastFlush = now
            }
        }
    }

    private fun reload() {
        policy.blocklist = BlocklistStore.loadNsfw(this)
        policy.bypassList = BlocklistStore.loadBypass(this)
    }

    override fun onRevoke() {
        // Un autre VPN a pris la place, ou l'utilisateur a coupé le VPN.
        Events.emit(Events.Type.REVOKED)
        notifyRevoked()
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        pool.shutdownNow()
        pool.awaitTermination(1, TimeUnit.SECONDS)
        super.onDestroy()
    }

    private fun shutdown() {
        worker?.interrupt()
        runCatching { tun?.close() }
        tun = null
        _running.value = false
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Protection active", NotificationManager.IMPORTANCE_MIN))
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Rempart te protège")
            .setContentText("Filtre réseau actif sur ce téléphone, sans serveur distant.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notifyRevoked() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "Alertes de protection", NotificationManager.IMPORTANCE_HIGH))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = launch?.let { PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE) }
        nm.notify(
            NOTIF_ALERT_ID,
            NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Le filtre réseau de Rempart est coupé")
                .setContentText("Touche ici pour le réactiver.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Points d'accroche pour le journal d'intégrité (branchés par le module :app). */
    object Events {
        enum class Type { STARTED, REVOKED }
        @Volatile var listener: ((Type) -> Unit)? = null
        fun emit(t: Type) = runCatching { listener?.invoke(t) }
    }

    companion object {
        private const val TAG = "RempartVpn"
        const val ACTION_START = "org.tidalex.rempart.vpn.START"
        const val ACTION_STOP = "org.tidalex.rempart.vpn.STOP"
        const val ACTION_RELOAD = "org.tidalex.rempart.vpn.RELOAD"
        const val TUN_ADDRESS = "10.111.222.1"
        const val TUN_DNS = "10.111.222.2"
        const val TUN_ADDRESS6 = "fdcc:7265:6d70::1"
        private const val CHANNEL = "protection"
        private const val CHANNEL_ALERT = "alerts"
        private const val NOTIF_ID = 1
        private const val NOTIF_ALERT_ID = 2

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        /** Intent de consentement à afficher, ou null si déjà accordé. */
        fun prepare(context: Context): Intent? = VpnService.prepare(context)

        fun start(context: Context) {
            FilterSettings(context).wanted = true
            ContextCompat.startForegroundService(context, Intent(context, RempartVpnService::class.java).setAction(ACTION_START))
        }

        /** Arrêt volontaire : l'app ne l'autorise que si le verrou est ouvert. */
        fun stop(context: Context) {
            FilterSettings(context).wanted = false
            context.startService(Intent(context, RempartVpnService::class.java).setAction(ACTION_STOP))
        }

        fun reloadBlocklist(context: Context) {
            if (_running.value) context.startService(Intent(context, RempartVpnService::class.java).setAction(ACTION_RELOAD))
        }
    }
}
