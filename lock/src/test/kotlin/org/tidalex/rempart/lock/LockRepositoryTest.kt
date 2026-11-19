package org.tidalex.rempart.lock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.tidalex.rempart.core.integrity.IntegrityEvent
import org.tidalex.rempart.core.lock.Phase
import org.tidalex.rempart.core.lock.TrustedClock
import org.tidalex.rempart.core.lock.UnlockResult

/** Tests d'intégration du verrou avec le vrai stockage Android (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class LockRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var elapsed = 5_000_000L
    private val clock = TrustedClock { elapsed }
    private fun repo() = LockRepository.createForTest(context, clock)

    @Test fun `lock survives a process restart (persistence)`() {
        repo().apply { setPartner("7KQ2-M9XD-H4TB", "Paul", "+33600000000"); engage() }
        val reopened = repo()
        assertThat(reopened.state.value.phase).isEqualTo(Phase.LOCKED)
        assertThat(reopened.state.value.partnerName).isEqualTo("Paul")
    }

    @Test fun `release countdown survives a process kill (force stop)`() {
        repo().apply { engage(); requestRelease() }
        elapsed += 40 * 3600_000L
        repo().tick()
        elapsed += 33 * 3600_000L
        val r = repo()
        assertThat(r.tick().phase).isEqualTo(Phase.OPEN)
        assertThat(r.log.value.map { it.event }).contains(IntegrityEvent.RELEASE_COMPLETED)
    }

    @Test fun `partner unlock and failed attempts are logged`() {
        val r = repo().apply { setPartner("7KQ2-M9XD-H4TB", null, null); engage() }
        assertThat(r.unlockWithPartnerCode("BADCODE99")).isInstanceOf(UnlockResult.WrongCode::class.java)
        assertThat(r.unlockWithPartnerCode("7kq2 m9xd h4tb")).isEqualTo(UnlockResult.Success)
        assertThat(r.log.value.map { it.event }).containsAtLeast(
            IntegrityEvent.LOCK_ENGAGED, IntegrityEvent.PARTNER_CODE_FAILED, IntegrityEvent.PARTNER_UNLOCK,
        ).inOrder()
    }

    @Test fun `brute force cooldown survives a restart`() {
        var r = repo().apply { setPartner("7KQ2-M9XD-H4TB", null, null); engage() }
        repeat(5) { r.unlockWithPartnerCode("WRONG000$it") }
        r = repo() // l'app est tuée puis relancée
        assertThat(r.unlockWithPartnerCode("7KQ2-M9XD-H4TB")).isInstanceOf(UnlockResult.CoolingDown::class.java)
    }

    @Test fun `editing storage to a garbage phase keeps the lock`() {
        repo().engage()
        context.getSharedPreferences("rempart_lock", Context.MODE_PRIVATE).edit().putString("phase", "OPENED_BY_HACK").commit()
        assertThat(repo().state.value.isLocked).isTrue()
    }

    @Test fun `admin cannot be deactivated through the app while locked`() {
        LockRepository.get(context).apply { if (!isLocked) engage() }
        assertThat(RempartAdminReceiver.deactivate(context)).isFalse()
    }
}
