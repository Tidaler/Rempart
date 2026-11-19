package org.tidalex.rempart.core.lock

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** Horloge simulée : `elapsed` imite SystemClock.elapsedRealtime(). */
class FakeClock(var elapsed: Long = 1_000_000) : TrustedClock {
    override fun elapsedRealtimeMs() = elapsed
    fun advanceHours(h: Double) { elapsed += (h * 3600_000).toLong() }
    /** Simule un redémarrage : l'horloge monotone repart de presque zéro. */
    fun reboot(uptimeAfterBootMs: Long = 30_000) { elapsed = uptimeAfterBootMs }
}

class LockEngineTest {
    private val clock = FakeClock()
    private val engine = LockEngine(clock)
    private val code = "7KQ2-M9XD-H4TB"

    private fun lockedWithPartner(): LockState =
        engine.engage(engine.setPartner(LockState(), code, "Paul", "+237600000000"))

    @Test fun `engage locks`() {
        val s = lockedWithPartner()
        assertThat(s.phase).isEqualTo(Phase.LOCKED)
        assertThat(s.isLocked).isTrue()
    }

    @Test fun `partner code unlocks, case and dashes ignored`() {
        val (s, r) = engine.unlockWithPartnerCode(lockedWithPartner(), "7kq2m9xdh4tb")
        assertThat(r).isEqualTo(UnlockResult.Success)
        assertThat(s.phase).isEqualTo(Phase.OPEN)
    }

    @Test fun `partner code also cancels a running release`() {
        val releasing = engine.requestRelease(lockedWithPartner())
        val (s, r) = engine.unlockWithPartnerCode(releasing, code)
        assertThat(r).isEqualTo(UnlockResult.Success)
        assertThat(s.release).isNull()
    }

    @Test fun `release requires the full 72 hours`() {
        var s = engine.requestRelease(lockedWithPartner())
        clock.advanceHours(71.9)
        s = engine.tick(s)
        assertThat(s.phase).isEqualTo(Phase.RELEASING)
        assertThat(s.release!!.remainingMs).isGreaterThan(0)
        clock.advanceHours(0.2)
        s = engine.tick(s)
        assertThat(s.phase).isEqualTo(Phase.OPEN)
    }

    @Test fun `release can be cancelled and progress is lost`() {
        var s = engine.requestRelease(lockedWithPartner())
        clock.advanceHours(50.0)
        s = engine.cancelRelease(engine.tick(s))
        assertThat(s.phase).isEqualTo(Phase.LOCKED)
        s = engine.requestRelease(s)
        clock.advanceHours(30.0)
        assertThat(engine.tick(s).phase).isEqualTo(Phase.RELEASING)
    }

    // --- Sécurité : changement d'heure, redémarrage ---

    @Test fun `changing the wall clock has no effect (only monotonic time counts)`() {
        // Le moteur ne lit jamais l'heure murale : avancer la date de 10 jours ne change rien.
        var s = engine.requestRelease(lockedWithPartner())
        s = engine.tick(s)
        assertThat(s.release!!.accumulatedMs).isEqualTo(0)
    }

    @Test fun `reboot never makes the countdown jump forward`() {
        var s = engine.requestRelease(lockedWithPartner())
        clock.advanceHours(10.0)
        s = engine.tick(s)
        val before = s.release!!.accumulatedMs
        clock.reboot(uptimeAfterBootMs = 60_000) // téléphone éteint 3 jours puis rallumé
        s = engine.tick(s)
        assertThat(s.release!!.accumulatedMs).isEqualTo(before + 60_000)
        assertThat(s.phase).isEqualTo(Phase.RELEASING)
    }

    @Test fun `countdown never counts more than real elapsed time across many reboots`() {
        var c = MonotonicCountdown.start(72 * 3600_000L, clock)
        var realOn = 0L
        repeat(50) { i ->
            val on = (i % 7 + 1) * 3600_000L
            clock.elapsed += on; realOn += on
            c = c.advance(clock)
            clock.reboot(uptimeAfterBootMs = 5_000); realOn += 5_000
            c = c.advance(clock)
        }
        assertThat(c.accumulatedMs).isAtMost(realOn)
    }

    // --- Sécurité : force brute du code ---

    @Test fun `wrong codes trigger an escalating cooldown`() {
        var s = lockedWithPartner()
        repeat(4) {
            val (n, r) = engine.unlockWithPartnerCode(s, "WRONGCODE$it")
            assertThat(r).isInstanceOf(UnlockResult.WrongCode::class.java)
            s = n
        }
        val (s5, r5) = engine.unlockWithPartnerCode(s, "WRONGCODE5")
        assertThat(r5).isInstanceOf(UnlockResult.CoolingDown::class.java)
        // Même le bon code est refusé pendant la pause.
        val (_, r6) = engine.unlockWithPartnerCode(s5, code)
        assertThat(r6).isInstanceOf(UnlockResult.CoolingDown::class.java)
        clock.advanceHours(1.0 / 60 + 0.001)
        val (s7, r7) = engine.unlockWithPartnerCode(s5, code)
        assertThat(r7).isEqualTo(UnlockResult.Success)
        assertThat(s7.failedAttempts).isEqualTo(0)
    }

    @Test fun `cooldown doubles and is capped at 24h`() {
        assertThat(LockEngine.cooldownFor(4)).isNull()
        assertThat(LockEngine.cooldownFor(5)).isEqualTo(60_000)
        assertThat(LockEngine.cooldownFor(6)).isEqualTo(120_000)
        assertThat(LockEngine.cooldownFor(100)).isEqualTo(24 * 3600_000L)
    }

    @Test fun `partner and delay cannot be changed while locked`() {
        val s = lockedWithPartner()
        assertThrows(IllegalStateException::class.java) { engine.setPartner(s, "NEWCODE123", null, null) }
        assertThrows(IllegalStateException::class.java) { engine.setDelayHours(s, 24) }
    }

    @Test fun `delay cannot go below 24 hours`() {
        assertThrows(IllegalArgumentException::class.java) { engine.setDelayHours(LockState(), 1) }
    }

    @Test fun `without partner only the delay works`() {
        val s = engine.engage(LockState())
        assertThat(engine.unlockWithPartnerCode(s, "ANYTHING1").second).isEqualTo(UnlockResult.NoPartner)
    }
}
