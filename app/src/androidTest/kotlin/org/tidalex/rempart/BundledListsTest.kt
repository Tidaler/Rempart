package org.tidalex.rempart

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.tidalex.rempart.filter.dns.BlocklistStore
import kotlin.system.measureTimeMillis

/**
 * Régression : les listes doivent se lire depuis le VRAI APK (le packaging
 * Android décompressait autrefois les fichiers « .gz » et le VPN ne démarrait pas).
 */
@RunWith(AndroidJUnit4::class)
class BundledListsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bundledAdultListLoadsFromApk() {
        val list = BlocklistStore.loadNsfw(context)
        assertThat(list.size).isGreaterThan(100_000)
        assertThat(list.matches("www.pornhub.com")).isTrue()
        assertThat(list.matches("www.wikipedia.org")).isFalse()
    }

    @Test fun bundledDohListLoadsFromApk() {
        val list = BlocklistStore.loadBypass(context)
        assertThat(list.size).isGreaterThan(1_000)
        assertThat(list.matches("dns.google")).isTrue()
    }

    /** Performance sur appareil : le chargement initial doit rester court. */
    @Test fun loadingIsFastOnDevice() {
        BlocklistStore.loadNsfw(context) // préchauffage
        val ms = measureTimeMillis { BlocklistStore.loadNsfw(context) }
        assertThat(ms).isLessThan(3_000)
    }
}
