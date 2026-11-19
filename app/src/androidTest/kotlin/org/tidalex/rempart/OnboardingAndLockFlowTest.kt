package org.tidalex.rempart

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tidalex.rempart.core.lock.Phase
import org.tidalex.rempart.lock.LockRepository

/**
 * Tests d'instrumentation de l'interface (sur émulateur ou téléphone).
 * L'état est préparé AVANT d'ouvrir l'écran, et le verrou est toujours rouvert
 * avec le code de test à la fin, pour ne jamais laisser l'appareil verrouillé.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingAndLockFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get() = LockRepository.get(context)
    private val testCode = "TEST-CODE-2026"
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun openLock() {
        if (repo.isLocked) repo.unlockWithPartnerCode(testCode)
    }

    @After fun close() {
        scenario?.close()
        if (repo.isLocked) repo.unlockWithPartnerCode(testCode)
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    private fun onboarded() {
        Onboarding.setConsented(context)
        Onboarding.setPartnerStepDone(context)
    }

    @Test fun welcomeRequiresSelfUseConsent() {
        context.getSharedPreferences("rempart_onboarding", 0).edit(commit = true) { clear() }
        launch()
        compose.onNodeWithTag("welcomeContinue").assertIsNotEnabled()
        compose.onNodeWithTag("selfUse").performClick()
        compose.onNodeWithTag("welcomeContinue").assertIsEnabled()
    }

    @Test fun partnerUnlockFlowThroughTheUi() {
        onboarded()
        repo.setPartner(testCode, "Test", null)
        repo.engage()
        launch()
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
        onboarded()
        repo.setPartner(testCode, "Test", null)
        repo.engage()
        launch()
        compose.onNodeWithTag("goUnlock").performScrollTo().performClick()
        compose.onNodeWithTag("releaseStart").performScrollTo().performClick()
        compose.waitUntil(3000) { repo.state.value.phase == Phase.RELEASING }
        compose.onNodeWithTag("releaseRunning").assertExists()
        compose.onNodeWithTag("releaseCancel").performScrollTo().performClick()
        compose.waitUntil(3000) { repo.state.value.phase == Phase.LOCKED }
    }
}
