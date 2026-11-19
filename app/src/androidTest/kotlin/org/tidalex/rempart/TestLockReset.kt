package org.tidalex.rempart

import android.content.Context
import org.tidalex.rempart.lock.LockRepository

/**
 * Remet le verrou à zéro entre deux tests, quel que soit l'état laissé par le test
 * précédent (autre code partenaire, essais ratés, pause en cours). Réservé aux tests :
 * l'appli elle-même n'a aucun moyen de faire ça.
 */
object TestLockReset {
    fun reset(context: Context) {
        context.getSharedPreferences("rempart_lock", Context.MODE_PRIVATE).edit().clear().commit()
        val field = LockRepository::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null) // la prochaine lecture repart du stockage vide
    }
}
