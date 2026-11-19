package org.tidalex.rempart.filter.image

/**
 * Détection d'images trop dénudées sur l'appareil — module OPTIONNEL, en préparation.
 *
 * Principe prévu : le service d'accessibilité (Android 11+, `takeScreenshot`)
 * fournit une capture réduite de l'écran quand un navigateur ou une appli à
 * risque est au premier plan ; un modèle TensorFlow Lite open source (licence
 * compatible GPL, ex. MobileNet entraîné sur 5 classes « drawings / hentai /
 * neutral / porn / sexy ») la classe localement. Aucune image n'est stockée ni envoyée.
 *
 * Ce fichier contient la partie testable sans Android : l'interface du
 * classifieur, la décision et le planificateur qui limite le coût batterie.
 */
interface NsfwClassifier {
    /** Probabilités par classe pour une image RGB réduite (ex. 224×224). */
    fun classify(rgb: IntArray, width: Int, height: Int): Scores
}

data class Scores(val porn: Float, val sexy: Float, val hentai: Float, val neutral: Float, val drawings: Float)

enum class Strictness(val pornThreshold: Float, val sexyThreshold: Float) {
    /** Nu explicite seulement. */
    STANDARD(0.70f, 0.95f),
    /** Nu et contenus très suggestifs (« nu aux 3/4 »), choix par défaut de Rempart. */
    STRICT(0.55f, 0.75f),
}

/** Décision sur plusieurs captures pour éviter qu'une seule erreur ferme une page. */
class ImageVerdict(private val strictness: Strictness = Strictness.STRICT, private val hitsNeeded: Int = 2, private val window: Int = 3) {
    private val recent = ArrayDeque<Boolean>()

    fun isExplicit(s: Scores): Boolean =
        s.porn + s.hentai >= strictness.pornThreshold || s.sexy >= strictness.sexyThreshold

    /** Ajoute un résultat, renvoie vrai s'il faut bloquer. */
    fun push(s: Scores): Boolean {
        recent.addLast(isExplicit(s))
        while (recent.size > window) recent.removeFirst()
        return recent.count { it } >= hitsNeeded
    }

    fun reset() = recent.clear()
}

/**
 * Quand faut-il capturer l'écran ? Jamais écran éteint, jamais en économie
 * d'énergie, seulement pour les applis à risque, au plus toutes les [minIntervalMs].
 */
class ScanScheduler(private val minIntervalMs: Long = 4_000, private val lowBatteryPercent: Int = 15) {
    private var lastScan = Long.MIN_VALUE / 2

    data class Context(
        val nowMs: Long,
        val screenOn: Boolean,
        val powerSaver: Boolean,
        val batteryPercent: Int,
        val foregroundIsRisky: Boolean,
        val contentChanged: Boolean,
    )

    fun shouldScan(c: Context): Boolean {
        if (!c.screenOn || c.powerSaver || !c.foregroundIsRisky) return false
        if (c.batteryPercent in 0 until lowBatteryPercent) return false
        if (!c.contentChanged) return false
        if (c.nowMs - lastScan < minIntervalMs) return false
        lastScan = c.nowMs
        return true
    }
}
