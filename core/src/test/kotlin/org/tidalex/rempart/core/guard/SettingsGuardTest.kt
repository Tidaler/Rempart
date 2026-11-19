package org.tidalex.rempart.core.guard

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsGuardTest {
    private val guard = SettingsGuard()
    private fun s(pkg: String, vararg texts: String) = ScreenSnapshot(pkg, null, texts.toList())

    @Test fun `app info page of Rempart is blocked (uninstall, force stop)`() {
        assertThat(guard.evaluate(s("com.android.settings", "Infos sur l'appli", "Rempart", "Désinstaller", "Forcer l'arrêt"), true))
            .isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "App info", "Rempart", "Uninstall", "Force stop"), true))
            .isEqualTo(GuardAction.LEAVE_SCREEN)
    }

    @Test fun `accessibility, device admin and VPN screens are blocked`() {
        assertThat(guard.evaluate(s("com.android.settings", "Rempart", "Utiliser Rempart"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "Applis d'administration de l'appareil", "Rempart"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "VPN", "Rempart", "Connecté"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
    }

    @Test fun `private DNS dialog is blocked even without our name`() {
        assertThat(guard.evaluate(s("com.android.settings", "DNS privé", "Désactivé", "Automatique"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
    }

    @Test fun `Samsung and Xiaomi settings packages are covered`() {
        assertThat(guard.evaluate(s("com.samsung.android.settings", "Rempart", "Désinstaller"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.miui.securitycenter", "Rempart", "Forcer l'arrêt"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
    }

    @Test fun `package installer uninstall dialog is blocked`() {
        assertThat(guard.evaluate(s("com.google.android.packageinstaller", "Voulez-vous désinstaller cette appli ?", "Rempart"), true))
            .isEqualTo(GuardAction.LEAVE_SCREEN)
    }

    @Test fun `play store uninstall button on our page is blocked`() {
        assertThat(guard.evaluate(s("com.android.vending", "Rempart", "Désinstaller", "Ouvrir"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.vending", "WhatsApp", "Désinstaller", "Ouvrir"), true)).isEqualTo(GuardAction.NONE)
    }

    @Test fun `ordinary settings stay usable`() {
        assertThat(guard.evaluate(s("com.android.settings", "Wi-Fi", "Bluetooth", "Affichage"), true)).isEqualTo(GuardAction.NONE)
        assertThat(guard.evaluate(s("com.android.settings", "WhatsApp", "Désinstaller", "Forcer l'arrêt"), true)).isEqualTo(GuardAction.NONE)
    }

    @Test fun `guard works whatever the phone language (es, pt, de)`() {
        assertThat(guard.evaluate(s("com.android.settings", "Rempart", "Desinstalar", "Forzar detención"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "Rempart", "Forçar parada"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "Rempart", "Deinstallieren", "Stopp erzwingen"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "DNS privado"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
    }

    @Test fun `battery and auto-start pages of Rempart are blocked while locked`() {
        assertThat(guard.evaluate(s("com.android.settings", "Optimisation de la batterie", "Rempart", "Ne pas optimiser"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.transsion.phonemaster", "Lancement automatique", "Rempart"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.miui.securitycenter", "Autostart", "Rempart"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "Battery usage", "Rempart", "Restricted"), true)).isEqualTo(GuardAction.LEAVE_SCREEN)
        assertThat(guard.evaluate(s("com.android.settings", "Optimisation de la batterie", "WhatsApp"), true)).isEqualTo(GuardAction.NONE)
    }

    @Test fun `nothing is blocked when the lock is open`() {
        assertThat(guard.evaluate(s("com.android.settings", "Rempart", "Désinstaller"), false)).isEqualTo(GuardAction.NONE)
    }

    @Test fun `our own app is never blocked`() {
        assertThat(guard.evaluate(s("org.tidalex.rempart", "Rempart", "Désactiver"), true)).isEqualTo(GuardAction.NONE)
    }
}
