package org.tidalex.rempart.filter.dns

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist
import org.tidalex.rempart.core.domain.Verdict
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Les services Google dont dépendent le téléphone, le Play Store et Play Protect
 * (analyse des applis installées hors Play Store) ne doivent jamais être bloqués
 * par les listes embarquées.
 */
class GoogleServicesNotBlockedTest {
    private val assets = File("src/main/assets")
    private val policy = ContentPolicy(
        blocklist = BlocklistStore.parseGz(File(assets, BlocklistStore.ASSET_NSFW).inputStream()),
        bypassList = DomainBlocklist.parse(GZIPInputStream(File(assets, BlocklistStore.ASSET_DOH).inputStream()).bufferedReader()).build(),
    )

    @Test fun `play store, play protect and core google services resolve normally`() {
        listOf(
            "play.googleapis.com", "play-fe.googleapis.com", "android.clients.google.com",
            "android.googleapis.com", "www.googleapis.com", "googleapis.com",
            "safebrowsing.googleapis.com", "safebrowsing.google.com", "playatoms-pa.googleapis.com",
            "play-lh.googleusercontent.com", "dl.google.com", "dl-ssl.google.com", "gvt1.com", "redirector.gvt1.com",
            "www.gstatic.com", "gstatic.com", "connectivitycheck.gstatic.com", "clients1.google.com",
            "clients2.google.com", "clients3.google.com", "clients4.google.com", "clients.google.com",
            "mtalk.google.com", "accounts.google.com", "android.apis.google.com",
            "firebaseinstallations.googleapis.com", "fcm.googleapis.com", "time.android.com",
            "www.google.com", "google.com", "youtube.com", "github.com", "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        ).forEach { host ->
            assertWithMessage(host).that(policy.checkHost(host)).isEqualTo(Verdict.Allow)
        }
    }
}
