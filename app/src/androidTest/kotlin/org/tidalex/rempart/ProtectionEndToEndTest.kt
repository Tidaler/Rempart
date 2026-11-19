package org.tidalex.rempart

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.tidalex.rempart.core.domain.SafeSearch
import org.tidalex.rempart.filter.accessibility.RempartAccessibilityService
import org.tidalex.rempart.filter.dns.RempartVpnService
import org.tidalex.rempart.lock.LockRepository
import java.net.InetAddress

/**
 * Tests de bout en bout sur un vrai Android (émulateur ou téléphone).
 * Préparation faite par tools/e2e_emulator.sh : consentement VPN accordé par
 * `appops`, accessibilité activée par `settings`, administrateur par `dpm`.
 */
@RunWith(AndroidJUnit4::class)
class ProtectionEndToEndTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val context = instr.targetContext
    private val device = UiDevice.getInstance(instr)
    private val partnerCode = "E2E-TEST-CODE-7"

    @Before fun startFilter() {
        RempartApp.wire(context)
        assumeTrue("consentement VPN requis (voir tools/e2e_emulator.sh)", RempartVpnService.prepare(context) == null)
        RempartVpnService.start(context)
        val t0 = System.currentTimeMillis()
        while (!RempartVpnService.running.value && System.currentTimeMillis() - t0 < 15_000) Thread.sleep(200)
        assertThat(RempartVpnService.running.value).isTrue()
        Thread.sleep(1500) // le système bascule le DNS sur le VPN
    }

    @After fun cleanUp() {
        val repo = LockRepository.get(context)
        if (repo.isLocked) repo.unlockWithPartnerCode(partnerCode)
        RempartVpnService.stop(context)
        device.pressHome()
    }

    private fun resolve(host: String): List<String> =
        runCatching { InetAddress.getAllByName(host).map { it.hostAddress!! } }.getOrDefault(emptyList())

    @Test fun adultDomainsResolveToNowhere() {
        listOf("pornhub.com", "www.xvideos.com", "m.xhamster.com").forEach { host ->
            val ips = resolve(host)
            assertThat(ips).isNotEmpty()
            assertThat(ips.all { it == "0.0.0.0" || it == "::" || it == "0:0:0:0:0:0:0:0" }).isTrue()
        }
    }

    @Test fun normalDomainsStillWork() {
        val ips = resolve("example.com")
        assertThat(ips).isNotEmpty()
        assertThat(ips).doesNotContain("0.0.0.0")
    }

    @Test fun googleIsForcedToSafeSearch() {
        val expected = resolve(SafeSearch.GOOGLE).toSet()
        assumeTrue(expected.isNotEmpty())
        assertThat(resolve("www.google.com").toSet()).isEqualTo(expected)
    }

    @Test fun dohProvidersAreBlocked() {
        assertThat(resolve("dns.google")).contains("0.0.0.0")
        assertThat(resolve("cloudflare-dns.com")).contains("0.0.0.0")
    }

    @Test fun incognitoBrowserPageIsClosedByAccessibility() {
        assumeTrue("accessibilité requise", RempartAccessibilityService.isEnabled(context))
        assumeTrue("Chrome requis", context.packageManager.getLaunchIntentForPackage("com.android.chrome") != null)
        // L'URL est tapée dans Chrome : même si le DNS bloque, l'accessibilité doit fermer la page.
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pornhub.com/")).setPackage("com.android.chrome")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val title = context.getString(R.string.blocked_title)
        assertThat(device.wait(Until.hasObject(By.text(title)), 20_000)).isTrue()
    }

    @Test fun protectedSettingsAreRefusedWhileLocked() {
        assumeTrue("accessibilité requise", RempartAccessibilityService.isEnabled(context))
        val repo = LockRepository.get(context)
        if (!repo.isLocked) { repo.setPartner(partnerCode, "E2E", null); repo.engage() }
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val title = context.getString(R.string.blocked_settings_title)
        assertThat(device.wait(Until.hasObject(By.text(title)), 15_000)).isTrue()
        assertThat(repo.log.value.map { it.event.name }).contains("PROTECTED_SCREEN_BLOCKED")
    }
}
