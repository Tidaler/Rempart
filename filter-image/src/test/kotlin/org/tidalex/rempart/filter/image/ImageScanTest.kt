package org.tidalex.rempart.filter.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageScanTest {
    private val neutral = Scores(0.02f, 0.05f, 0.01f, 0.9f, 0.02f)
    private val sexy = Scores(0.1f, 0.8f, 0.0f, 0.1f, 0.0f)
    private val porn = Scores(0.9f, 0.05f, 0.0f, 0.05f, 0.0f)

    @Test fun `strict mode blocks very suggestive images, standard only explicit`() {
        assertThat(ImageVerdict(Strictness.STRICT).isExplicit(sexy)).isTrue()
        assertThat(ImageVerdict(Strictness.STANDARD).isExplicit(sexy)).isFalse()
        assertThat(ImageVerdict(Strictness.STANDARD).isExplicit(porn)).isTrue()
        assertThat(ImageVerdict().isExplicit(neutral)).isFalse()
    }

    @Test fun `a single false positive does not block`() {
        val v = ImageVerdict()
        assertThat(v.push(porn)).isFalse()
        assertThat(v.push(neutral)).isFalse()
        assertThat(v.push(neutral)).isFalse()
        assertThat(v.push(porn)).isFalse()
        assertThat(v.push(porn)).isTrue()
    }

    private fun ctx(t: Long, screenOn: Boolean = true, saver: Boolean = false, battery: Int = 80, risky: Boolean = true, changed: Boolean = true) =
        ScanScheduler.Context(t, screenOn, saver, battery, risky, changed)

    @Test fun `scheduler respects battery constraints`() {
        val s = ScanScheduler()
        assertThat(s.shouldScan(ctx(0, screenOn = false))).isFalse()
        assertThat(s.shouldScan(ctx(0, saver = true))).isFalse()
        assertThat(s.shouldScan(ctx(0, battery = 10))).isFalse()
        assertThat(s.shouldScan(ctx(0, risky = false))).isFalse()
        assertThat(s.shouldScan(ctx(0, changed = false))).isFalse()
        assertThat(s.shouldScan(ctx(0))).isTrue()
    }

    @Test fun `at most one scan every 4 seconds (battery budget)`() {
        val s = ScanScheduler()
        var scans = 0
        for (t in 0L until 60_000L step 100) if (s.shouldScan(ctx(t))) scans++
        assertThat(scans).isAtMost(15) // ≤ 15 captures par minute de navigation à risque
    }
}
