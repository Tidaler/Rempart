package org.tidalex.rempart.filter.dns

import android.content.Context
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist

/**
 * Une seule copie des listes en mémoire (~1,2 Mo), partagée par le VPN et le
 * service d'accessibilité qui tournent dans le même processus.
 */
object SharedPolicy {
    private val policy = ContentPolicy(DomainBlocklist.EMPTY)
    @Volatile private var loaded = false

    fun get(context: Context): ContentPolicy {
        if (!loaded) synchronized(this) { if (!loaded) { reload(context); loaded = true } }
        return policy
    }

    fun reload(context: Context) {
        val app = context.applicationContext
        policy.blocklist = BlocklistStore.loadNsfw(app)
        policy.bypassList = BlocklistStore.loadBypass(app)
    }
}
