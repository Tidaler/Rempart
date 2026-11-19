package org.tidalex.rempart.core.guard

/** Ce que le service d'accessibilité voit d'une fenêtre (sans rien stocker). */
data class ScreenSnapshot(
    val packageName: String,
    val className: String? = null,
    val texts: List<String>,
)

enum class GuardAction { NONE, LEAVE_SCREEN }

/**
 * Quand le verrou est actif, empêche d'atteindre les écrans qui permettraient
 * de couper la protection : fiche de l'app (désinstaller / forcer l'arrêt),
 * accessibilité de Rempart, administrateurs de l'appareil, VPN, DNS privé,
 * désinstallation via le Play Store ou l'installateur de paquets.
 *
 * Règles volontairement textuelles et multilingues (FR/EN), car chaque
 * constructeur (Samsung, Xiaomi, Tecno, Huawei…) a ses propres écrans.
 */
class SettingsGuard(private val appLabel: String = "Rempart", private val ownPackage: String = "org.tidalex.rempart") {

    private val settingsPackages = setOf(
        "com.android.settings", "com.samsung.android.settings", "com.miui.securitycenter",
        "com.coloros.safecenter", "com.oplus.safecenter", "com.huawei.systemmanager",
        "com.transsion.phonemaster", "com.vivo.permissionmanager", "com.oneplus.security",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.samsung.android.lool", "com.samsung.android.sm.devicesecurity",
    )

    private val installerPackages = setOf(
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
    )

    private val storePackages = setOf("com.android.vending")

    /** Mots indiquant une action dangereuse quand l'écran mentionne Rempart. */
    private val dangerousWithLabel = listOf(
        "désinstaller", "desinstaller", "uninstall", "forcer l'arrêt", "forcer l’arrêt", "force stop",
        "arrêter", "stop", "désactiver", "deactivate", "administrat", "device admin",
        "accessibilit", "vpn", "effacer les données", "vider le stockage", "clear storage", "clear data",
        "oublier", "forget", "utiliser rempart", "use rempart",
    )

    /** Écrans dangereux même sans mention de Rempart. */
    private val alwaysDangerous = listOf(
        "dns privé", "private dns", "nom d'hôte du fournisseur dns", "private dns provider hostname",
        "mode sans échec", "safe mode",
    )

    fun evaluate(screen: ScreenSnapshot, locked: Boolean): GuardAction {
        if (!locked) return GuardAction.NONE
        if (screen.packageName == ownPackage) return GuardAction.NONE
        val lower = screen.texts.map { it.lowercase() }
        val mentionsUs = lower.any { it.contains(appLabel.lowercase()) || it.contains(ownPackage) }

        return when (screen.packageName) {
            in installerPackages -> if (mentionsUs) GuardAction.LEAVE_SCREEN else GuardAction.NONE
            in storePackages -> if (mentionsUs && lower.any { t -> t == "désinstaller" || t == "uninstall" }) GuardAction.LEAVE_SCREEN else GuardAction.NONE
            in settingsPackages -> when {
                alwaysDangerous.any { d -> lower.any { it.contains(d) } } -> GuardAction.LEAVE_SCREEN
                mentionsUs && dangerousWithLabel.any { d -> lower.any { it.contains(d) } } -> GuardAction.LEAVE_SCREEN
                else -> GuardAction.NONE
            }
            else -> GuardAction.NONE
        }
    }
}
