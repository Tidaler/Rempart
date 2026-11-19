package org.tidalex.rempart

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.tidalex.rempart.filter.accessibility.RempartAccessibilityService
import org.tidalex.rempart.filter.dns.FilterSettings
import org.tidalex.rempart.filter.dns.RempartVpnService
import org.tidalex.rempart.lock.DeviceOwnerPolicies
import org.tidalex.rempart.lock.LockRepository
import org.tidalex.rempart.lock.RempartAdminReceiver

/** Instantané de l'état des protections, pour l'interface. */
data class ProtectionStatus(
    val vpnRunning: Boolean,
    val vpnConsented: Boolean,
    val accessibility: Boolean,
    val admin: Boolean,
    val deviceOwner: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
    val autoStartAvailable: Boolean,
    val autoStartAsked: Boolean,
) {
    val allEssential: Boolean get() = vpnRunning && accessibility && admin && battery
}

object Protection {
    fun status(c: Context) = ProtectionStatus(
        vpnRunning = RempartVpnService.running.value,
        vpnConsented = RempartVpnService.prepare(c) == null,
        accessibility = RempartAccessibilityService.isEnabled(c),
        admin = RempartAdminReceiver.isActive(c),
        deviceOwner = DeviceOwnerPolicies.isDeviceOwner(c),
        notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        battery = Battery.isUnrestricted(c),
        autoStartAvailable = AutoStart.isAvailable(c),
        autoStartAsked = AutoStart.wasAsked(c),
    )

    /** Relance le filtre s'il devrait tourner et que le consentement VPN est toujours valable. */
    fun ensureVpnRunning(c: Context) {
        val wanted = FilterSettings(c).wanted || LockRepository.get(c).isLocked
        if (wanted && !RempartVpnService.running.value && RempartVpnService.prepare(c) == null) {
            runCatching { RempartVpnService.start(c) }
        }
    }

    fun accessibilitySettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun vpnSettingsIntent() = Intent(Settings.ACTION_VPN_SETTINGS)
    fun appLocaleIntent(c: Context): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(android.net.Uri.fromParts("package", c.packageName, null))
    } else null

    /** Désactivation complète : refusée tant que le verrou est actif. */
    fun turnEverythingOff(c: Context): Boolean {
        if (LockRepository.get(c).isLocked) return false
        RempartVpnService.stop(c)
        RempartAdminReceiver.deactivate(c)
        RempartAccessibilityService.turnOff() // sinon les pages restaient fermées après « Tout couper »
        return true
    }
}
