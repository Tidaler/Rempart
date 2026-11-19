package org.tidalex.rempart.lock

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import org.tidalex.rempart.core.integrity.IntegrityEvent

/**
 * Administrateur de l'appareil : tant qu'il est actif, Android refuse de
 * désinstaller Rempart. Le garde des Réglages (accessibilité) empêche de
 * le désactiver pendant le verrouillage.
 */
class RempartAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val repo = LockRepository.get(context)
        if (repo.isLocked) repo.record(IntegrityEvent.ADMIN_DISABLE_REQUESTED)
        return context.getString(R.string.admin_disable_warning)
    }

    companion object {
        fun component(context: Context) = ComponentName(context, RempartAdminReceiver::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        /** Intent système d'activation de l'administrateur (l'utilisateur doit accepter). */
        fun activationIntent(context: Context, explanation: String): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)

        /** Retrait de l'administrateur : autorisé seulement quand le verrou est ouvert. */
        fun deactivate(context: Context): Boolean {
            if (LockRepository.get(context).isLocked) return false
            val dpm = context.getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(context.packageName)) DeviceOwnerPolicies.release(context)
            if (dpm.isAdminActive(component(context))) dpm.removeActiveAdmin(component(context))
            return true
        }
    }
}

/**
 * Mode « propriétaire de l'appareil » (Device Owner), optionnel et avancé :
 * activé par ADB sur un téléphone sans compte (voir docs/DEVICE_OWNER.md).
 * Il permet ce qu'un simple administrateur ne peut pas faire.
 * La réinitialisation d'usine n'est volontairement JAMAIS bloquée.
 */
object DeviceOwnerPolicies {
    private val RESTRICTIONS = buildList {
        add(UserManager.DISALLOW_SAFE_BOOT)        // mode sans échec (désactive les applis tierces)
        add(UserManager.DISALLOW_ADD_USER)         // un autre utilisateur échapperait au filtre
        add(UserManager.DISALLOW_USER_SWITCH)
        add(UserManager.DISALLOW_CONFIG_VPN)       // remplacer Rempart par un autre VPN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
    }

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

    /** À appeler quand le verrou s'engage (et au démarrage) si l'app est propriétaire. */
    fun apply(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = RempartAdminReceiver.component(context)
        dpm.setUninstallBlocked(admin, context.packageName, true)
        RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
        runCatching { dpm.setAlwaysOnVpnPackage(admin, context.packageName, true) } // VPN permanent + blocage sans VPN
    }

    /** Levée de toutes les restrictions (verrou ouvert uniquement). */
    fun release(context: Context) {
        if (!isDeviceOwner(context) || LockRepository.get(context).isLocked) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = RempartAdminReceiver.component(context)
        dpm.setUninstallBlocked(admin, context.packageName, false)
        RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
        runCatching { dpm.setAlwaysOnVpnPackage(admin, null, false) }
        @Suppress("DEPRECATION") dpm.clearDeviceOwnerApp(context.packageName)
    }
}
