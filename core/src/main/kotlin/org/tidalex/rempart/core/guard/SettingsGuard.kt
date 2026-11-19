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
        // es / pt / de / it
        "desinstalar", "forzar detención", "forzar cierre", "forçar parada", "forçar interrupção",
        "deinstallieren", "stopp erzwingen", "beenden erzwingen", "disinstalla", "termina",
        "desactivar", "desativar", "deaktivieren", "disattiva", "accesibilidad", "acessibilidade",
        "bedienungshilfen", "accessibilità", "borrar datos", "limpar dados", "daten löschen",
        "usar rempart", "rempart verwenden", "usa rempart",
    )

    /**
     * Actions qui coupent vraiment Rempart. Ce sont les seules surveillées pendant
     * [PowerScreensGrace] : les listes d'applis des écrans batterie ou lancement
     * automatique contiennent souvent « VPN », « Stop »… dans le nom d'autres applis.
     */
    private val strongWithLabel = listOf(
        "désinstaller", "desinstaller", "uninstall", "forcer l'arrêt", "forcer l’arrêt", "force stop",
        "effacer les données", "vider le stockage", "clear storage", "clear data",
        "utiliser rempart", "use rempart", "usar rempart", "rempart verwenden", "usa rempart",
        "d'administration", "device admin", "administrateur de l'appareil",
        "oublier", "forget", "déconnecter", "disconnect",
        "desinstalar", "forzar detención", "forzar cierre", "forçar parada", "forçar interrupção",
        "deinstallieren", "stopp erzwingen", "beenden erzwingen", "disinstalla",
        "borrar datos", "limpar dados", "daten löschen",
    )

    /**
     * Batterie et lancement automatique : les restreindre laisserait le téléphone couper
     * Rempart. Mais ce sont aussi les écrans qui les ACCORDENT : quand Rempart ouvre
     * lui-même l'un d'eux, [PowerScreensGrace] les laisse passer quelques minutes.
     */
    private val powerWithLabel = listOf(
        "batterie", "battery", "batería", "bateria", "akku", "optimis", "optimiz",
        "lancement auto", "démarrage auto", "auto-start", "autostart", "auto start", "auto-launch", "autolaunch",
        "arrière-plan", "background", "restreindre", "restrict",
    )

    /** Écrans dangereux même sans mention de Rempart. */
    private val alwaysDangerous = listOf(
        "dns privé", "private dns", "nom d'hôte du fournisseur dns", "private dns provider hostname",
        "mode sans échec", "safe mode", "dns privado", "privates dns", "dns privato",
        "modo seguro", "abgesicherter modus", "modalità provvisoria",
    )

    /**
     * Règle qui a fait quitter le dernier écran (paquet + mot-clé), pour le diagnostic.
     * Jamais le texte de l'écran lui-même.
     */
    @Volatile var lastReason: String? = null
        private set

    fun evaluate(screen: ScreenSnapshot, locked: Boolean): GuardAction {
        if (!locked) return GuardAction.NONE
        if (screen.packageName == ownPackage) return GuardAction.NONE
        // Seuls les réglages, l'installateur et le Play Store sont surveillés : inutile de
        // préparer le texte des autres applis (navigateurs…), appelées bien plus souvent.
        val pkg = screen.packageName
        if (pkg !in settingsPackages && pkg !in installerPackages && pkg !in storePackages) return GuardAction.NONE
        val lower = screen.texts.map { it.lowercase() }
        val mentionsUs = lower.any { it.contains(appLabel.lowercase()) || it.contains(ownPackage) }

        fun hit(words: List<String>) = words.firstOrNull { d -> lower.any { it.contains(d) } }
        lastReason = null
        fun leave(reason: String): GuardAction { lastReason = "$pkg: $reason"; return GuardAction.LEAVE_SCREEN }

        return when (screen.packageName) {
            in installerPackages -> if (mentionsUs) leave("installateur") else GuardAction.NONE
            in storePackages -> if (mentionsUs && lower.any { t -> t == "désinstaller" || t == "uninstall" }) leave("store") else GuardAction.NONE
            in settingsPackages -> {
                val always = hit(alwaysDangerous)
                val grace = PowerScreensGrace.isOpen()
                val danger = if (mentionsUs) hit(if (grace) strongWithLabel else dangerousWithLabel) else null
                val power = if (mentionsUs && !grace) hit(powerWithLabel) else null
                when {
                    always != null -> leave(always)
                    danger != null -> leave(danger)
                    power != null -> leave(power)
                    else -> GuardAction.NONE
                }
            }
            else -> GuardAction.NONE
        }
    }
}

/**
 * Courte fenêtre pendant laquelle les écrans batterie et lancement automatique de
 * Rempart restent ouverts, parce que c'est Rempart qui vient de les ouvrir pour que
 * l'utilisateur accorde ces droits. Les écrans vraiment dangereux (désinstaller,
 * forcer l'arrêt, accessibilité, administrateur, VPN) restent bloqués pendant ce temps.
 * Horloge monotone : changer l'heure du téléphone ne l'allonge pas.
 */
object PowerScreensGrace {
    const val DURATION_MS = 2 * 60_000L
    @Volatile private var openUntilNanos = Long.MIN_VALUE

    fun open(nowNanos: Long = System.nanoTime()) { openUntilNanos = nowNanos + DURATION_MS * 1_000_000 }
    fun close() { openUntilNanos = Long.MIN_VALUE }
    fun isOpen(nowNanos: Long = System.nanoTime()): Boolean = openUntilNanos != Long.MIN_VALUE && nowNanos - openUntilNanos < 0
}
