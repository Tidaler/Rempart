package org.tidalex.rempart.filter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.tidalex.rempart.core.domain.BlockReason
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.lock.LockRepository

/**
 * Service d'accessibilité de Rempart. Il lit (sans rien enregistrer ni envoyer)
 * le texte des fenêtres pour :
 * 1. fermer les pages interdites dans les navigateurs (y compris en navigation
 *    privée) et dans les navigateurs intégrés d'autres applis ;
 * 2. quand le verrou est actif, refuser l'accès aux réglages qui permettraient
 *    de couper la protection.
 */
class RempartAccessibilityService : AccessibilityService() {

    private val analyzer by lazy { ScreenAnalyzer(policyProvider = { Hooks.policy?.invoke(this) ?: EMPTY_POLICY }) }
    private val lastCheck = HashMap<String, Long>()
    private var lastAction = 0L
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        connected = true
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = SystemClock.uptimeMillis()
        // Limitation de fréquence : un même paquet est analysé au plus toutes les 350 ms
        // pour les changements de contenu (défilement, frappe), pour économiser la batterie.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (now - (lastCheck[pkg] ?: 0L) < THROTTLE_MS) return
        }
        lastCheck[pkg] = now

        val root = rootInActiveWindow ?: return
        val locked = LockRepository.get(this).isLocked
        val decision = analyzer.analyze(pkg, AndroidNode(root), locked, event.text ?: emptyList())
        when (decision) {
            ScreenDecision.Nothing -> Unit
            is ScreenDecision.BlockContent -> act(now) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                main.postDelayed({ showBlockedScreen(decision.reason) }, GLOBAL_ACTION_SETTLE_MS)
                Hooks.onBlocked?.invoke(this, decision.reason)
            }
            ScreenDecision.LeaveProtectedSettings -> act(now) {
                Log.i(TAG, "écran protégé quitté (${analyzer.guard.lastReason})") // règle seulement, jamais le texte lu
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                LockRepository.get(this).record(IntegrityEvent.PROTECTED_SCREEN_BLOCKED)
                // Les actions globales sont asynchrones : sans délai, l'écran d'accueil
                // passerait PAR-DESSUS l'écran d'explication.
                main.postDelayed({ showBlockedScreen(BlockReason.PROTECTED_SETTINGS) }, GLOBAL_ACTION_SETTLE_MS)
            }
        }
    }

    private inline fun act(now: Long, block: () -> Unit) {
        if (now - lastAction < 800) return // évite les boucles d'actions
        lastAction = now
        block()
    }

    private fun showBlockedScreen(reason: BlockReason) {
        val intent = Intent(ACTION_BLOCKED).setPackage(packageName)
            .putExtra(EXTRA_REASON, reason.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connected = false
        instance = null
        val repo = LockRepository.get(this)
        if (repo.isLocked) repo.record(IntegrityEvent.ACCESSIBILITY_LOST)
        return super.onUnbind(intent)
    }

    /** Adaptateur Android → [NodeView]. */
    private class AndroidNode(private val n: AccessibilityNodeInfo) : NodeView {
        override val text get() = n.text
        override val contentDescription get() = n.contentDescription
        override val viewId: String? get() = n.viewIdResourceName
        override val className get() = n.className
        override val childCount get() = n.childCount
        override fun child(i: Int): NodeView? = n.getChild(i)?.let(::AndroidNode)
    }

    /** Points d'accroche fournis par le module :app. */
    object Hooks {
        @Volatile var policy: ((Context) -> ContentPolicy)? = null
        @Volatile var onBlocked: ((Context, BlockReason) -> Unit)? = null
    }

    companion object {
        private const val TAG = "RempartGuard"
        const val ACTION_BLOCKED = "org.tidalex.rempart.action.BLOCKED"
        const val EXTRA_REASON = "reason"
        private const val THROTTLE_MS = 350L
        private const val GLOBAL_ACTION_SETTLE_MS = 400L
        private val EMPTY_POLICY = ContentPolicy(DomainBlocklist.EMPTY)

        @Volatile var connected = false
            private set

        @Volatile private var instance: RempartAccessibilityService? = null

        /** Arrêt demandé depuis l'appli (protection ouverte seulement, vérifié par l'appelant). */
        fun turnOff() {
            instance?.let { svc -> svc.main.post { runCatching { svc.disableSelf() } } }
        }

        /** Vérité système : le service est-il activé dans les réglages ? */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, RempartAccessibilityService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
