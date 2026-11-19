package org.tidalex.rempart

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * Tout ce que Rempart doit obtenir de l'utilisateur pour protéger vraiment.
 * Android ne laisse aucune appli s'accorder ces droits seule : on les demande
 * donc dès la première ouverture, puis de nouveau après chaque mise à jour
 * s'il en manque un.
 */
enum class PermissionStep { NOTIFICATIONS, VPN, BATTERY, ADMIN, AUTOSTART, ACCESSIBILITY }

object Permissions {
    /** Désactivé par les tests d'interface, qui préparent l'appareil eux-mêmes. */
    @Volatile var autoPrompt = true

    fun missing(c: Context): List<PermissionStep> {
        val s = Protection.status(c)
        return buildList {
            if (!s.notifications) add(PermissionStep.NOTIFICATIONS)
            if (!s.vpnConsented || !s.vpnRunning) add(PermissionStep.VPN)
            if (!s.battery) add(PermissionStep.BATTERY)
            if (!s.admin) add(PermissionStep.ADMIN)
            if (s.autoStartAvailable && !s.autoStartAsked) add(PermissionStep.AUTOSTART)
            if (!s.accessibility) add(PermissionStep.ACCESSIBILITY)
        }
    }

    /** Les protections sans lesquelles Rempart ne peut pas faire son travail. */
    fun missingEssential(c: Context): List<PermissionStep> =
        missing(c).filter { it != PermissionStep.NOTIFICATIONS && it != PermissionStep.AUTOSTART }
}

/** Exemption d'optimisation de batterie : sans elle, Android peut couper le filtre. */
object Battery {
    fun isUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /** Boîte de dialogue système « Autoriser Rempart à toujours fonctionner en arrière-plan ? ». */
    @SuppressLint("BatteryLife") // filtre de contenu permanent : cas prévu par la règle Google Play
    fun requestIntent(c: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${c.packageName}".toUri())

    /** Liste générale, si la boîte de dialogue n'existe pas sur ce téléphone. */
    fun fallbackIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

/**
 * Lancement automatique. Plusieurs constructeurs (Tecno, Infinix, Xiaomi, Oppo,
 * Vivo, Huawei…) empêchent les applis de redémarrer seules tant qu'on ne les a
 * pas autorisées dans leur propre écran. Il n'existe aucune API pour lire ce
 * réglage : on ouvre le bon écran et on retient que l'utilisateur y est passé.
 */
object AutoStart {
    private val CANDIDATES = listOf(
        // Tecno, Infinix, itel (Transsion, HiOS / XOS)
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        "com.transsion.phonemaster" to "com.cyin.himgr.applicationmanager.view.activities.AppManagerActivity",
        // Xiaomi, Redmi, Poco
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Oppo, Realme, OnePlus récents
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // Vivo, iQOO
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Huawei, Honor
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        // OnePlus anciens, Asus, Samsung
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
    )

    private fun prefs(c: Context) = c.getSharedPreferences("rempart_autostart", Context.MODE_PRIVATE)

    /** Premier écran constructeur qui existe vraiment sur ce téléphone, ou null. */
    fun intent(c: Context): Intent? = CANDIDATES.asSequence()
        .map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        .firstOrNull { isLaunchable(c, it) }

    private fun isLaunchable(c: Context, i: Intent): Boolean = runCatching {
        val info = c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) ?: return false
        info.activityInfo.exported
    }.getOrDefault(false)

    @Volatile private var available: Boolean? = null

    /** Le résultat ne change pas tant que l'appli tourne : calculé une seule fois. */
    fun isAvailable(c: Context): Boolean = available ?: (intent(c) != null).also { available = it }

    /** Retenu par version de l'appli : après une mise à jour, certains téléphones remettent le réglage à zéro. */
    fun wasAsked(c: Context): Boolean = prefs(c).getInt("askedVersion", -1) == BuildConfig.VERSION_CODE

    fun markAsked(c: Context) = prefs(c).edit { putInt("askedVersion", BuildConfig.VERSION_CODE) }
}
