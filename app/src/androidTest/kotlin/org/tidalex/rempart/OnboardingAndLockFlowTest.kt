package org.tidalex.rempart

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tidalex.rempart.core.lock.Phase
import org.tidalex.rempart.lock.LockRepository

/**
 * Tests d'instrumentation de l'interface (sur émulateur ou téléphone).
 * Ils manipulent le verrou directement et le rouvrent toujours à la fin avec le
 * code du partenaire de test, pour ne jamais laisser l'appareil de test verrouillé.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingAndLockFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get() = LockRepository.get(context)
    private val testCode = "TEST-CODE-2026"

    @Before fun openLock() {
        if (repo.isLocked) repo.unlockWithPartnerCode(testCode)
    }

    @Test fun welcomeRequiresSelfUseConsent() {
        context.getSharedPreferences("rempart_onboarding", 0).edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("welcomeContinue").assertIsNotEnabled()
        compose.onNodeWithTag("selfUse").performClick()
        compose.onNodeWithTag("welcomeContinue").assertIsEnabled()
    }

    @Test fun partnerUnlockFlowThroughTheUi() {
        Onboarding.setConsented(context); Onboarding.setPartnerStepDone(context)
        repo.setPartner(testCode, "Test", null)
        repo.engage()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("goUnlock").performScrollTo().performClick()
        compose.onNodeWithTag("unlockCode").performTextInput("WRONG-CODE-1")
        compose.onNodeWithTag("unlockSubmit").performClick()
        compose.onNodeWithTag("unlockMessage").assertExists()
        assertThat(repo.isLocked).isTrue()
        compose.onNodeWithTag("unlockCode").performTextInput(testCode.lowercase())
        compose.onNodeWithTag("unlockSubmit").performClick()
        compose.waitUntil(3000) { !repo.isLocked }
        assertThat(repo.state.value.phase).isEqualTo(Phase.OPEN)
    }

    @Test fun delayedReleaseCanBeStartedAndCancelled() {
        Onboarding.setConsented(context); Onboarding.setPartnerStepDone(context)
        repo.setPartner(testCode, "Test", null)
        repo.engage()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("goUnlock").performScrollTo().performClick()
        compose.onNodeWithTag("releaseStart").performScrollTo().performClick()
        compose.onNodeWithTag("releaseRunning").assertExists()
        assertThat(repo.state.value.phase).isEqualTo(Phase.RELEASING)
        compose.onNodeWithTag("releaseCancel").performScrollTo().performClick()
        compose.waitUntil(3000) { repo.state.value.phase == Phase.LOCKED }
        repo.unlockWithPartnerCode(testCode)
    }
}
