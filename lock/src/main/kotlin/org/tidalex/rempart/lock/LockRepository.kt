package org.tidalex.rempart.lock

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tidalex.rempart.core.integrity.IntegrityEntry
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.core.integrity.IntegrityLogCodec
import org.tidalex.rempart.core.lock.LockEngine
import org.tidalex.rempart.core.lock.LockState
import org.tidalex.rempart.core.lock.LockStateCodec
import org.tidalex.rempart.core.lock.Phase
import org.tidalex.rempart.core.lock.TrustedClock
import org.tidalex.rempart.core.lock.UnlockResult

/**
 * Accès unique (singleton de processus) à l'état du verrou. Écritures synchrones
 * (`commit`) : un arrêt brutal juste après un changement ne doit pas le perdre.
 */
class LockRepository private constructor(
    context: Context,
    clock: TrustedClock = TrustedClock { SystemClock.elapsedRealtime() },
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rempart_lock", Context.MODE_PRIVATE)
    private val logPrefs: SharedPreferences = context.getSharedPreferences("rempart_integrity", Context.MODE_PRIVATE)
    private val engine = LockEngine(clock)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LockState> = _state

    private val _log = MutableStateFlow(IntegrityLogCodec.decode(logPrefs.getString("log", "") ?: ""))
    val log: StateFlow<List<IntegrityEntry>> = _log

    val isLocked: Boolean get() = _state.value.isLocked

    @Synchronized fun tick(): LockState {
        val before = _state.value
        val after = engine.tick(before)
        if (after != before) {
            save(after)
            if (before.phase == Phase.RELEASING && after.phase == Phase.OPEN) record(IntegrityEvent.RELEASE_COMPLETED)
        }
        return after
    }

    @Synchronized fun setPartner(code: String?, name: String?, phone: String?) = save(engine.setPartner(tick(), code, name, phone))

    @Synchronized fun setDelayHours(hours: Int) = save(engine.setDelayHours(tick(), hours))

    @Synchronized fun engage() { save(engine.engage(tick())); record(IntegrityEvent.LOCK_ENGAGED) }

    @Synchronized fun requestRelease() { save(engine.requestRelease(tick())); record(IntegrityEvent.RELEASE_REQUESTED) }

    @Synchronized fun cancelRelease() { save(engine.cancelRelease(tick())); record(IntegrityEvent.RELEASE_CANCELLED) }

    @Synchronized fun unlockWithPartnerCode(code: String): UnlockResult {
        val (next, result) = engine.unlockWithPartnerCode(tick(), code)
        save(next)
        when (result) {
            UnlockResult.Success -> record(IntegrityEvent.PARTNER_UNLOCK)
            is UnlockResult.WrongCode, is UnlockResult.CoolingDown -> if (next.failedAttempts > 0) record(IntegrityEvent.PARTNER_CODE_FAILED)
            else -> Unit
        }
        return result
    }

    @Synchronized fun record(event: IntegrityEvent) {
        val updated = IntegrityLogCodec.append(_log.value, IntegrityEntry(System.currentTimeMillis(), event))
        logPrefs.edit().putString("log", IntegrityLogCodec.encode(updated)).commit()
        _log.value = updated
    }

    private fun load(): LockState = LockStateCodec.decode(prefs.all.mapValues { it.value.toString() })

    @SuppressLint("ApplySharedPref")
    private fun save(s: LockState) {
        val e = prefs.edit().clear()
        LockStateCodec.encode(s).forEach { (k, v) -> e.putString(k, v) }
        e.commit()
        _state.value = s
    }

    companion object {
        @Volatile private var instance: LockRepository? = null

        fun get(context: Context): LockRepository = instance ?: synchronized(this) {
            instance ?: LockRepository(context.applicationContext).also { instance = it }
        }

        /** Pour les tests : horloge contrôlée, instance neuve. */
        fun createForTest(context: Context, clock: TrustedClock) = LockRepository(context, clock)
    }
}
