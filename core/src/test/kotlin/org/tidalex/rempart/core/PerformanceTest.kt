package org.tidalex.rempart.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.tidalex.rempart.core.domain.ContentPolicy
import org.tidalex.rempart.core.domain.DomainBlocklist
import org.tidalex.rempart.core.progress.Streak
import java.time.LocalDate
import kotlin.system.measureNanoTime

/**
 * Tests de performance : le filtre est sur le chemin de CHAQUE requête DNS et de
 * chaque événement d'accessibilité. Seuils larges (machine de CI lente) mais qui
 * détectent une régression d'un ordre de grandeur.
 */
class PerformanceTest {

    private val domains = (0 until 200_000).map { "site-$it.example${it % 50}.com" }

    @Test fun `building a 200k list is fast and compact`() {
        lateinit var list: DomainBlocklist
        val ms = measureNanoTime { list = DomainBlocklist.of(domains) } / 1e6
        assertThat(list.size).isEqualTo(200_000)
        assertThat(ms).isLessThan(3_000.0)
        // 8 octets par domaine.
        assertThat(list.toLongArray().size * 8).isAtMost(1_700_000)
    }

    @Test fun `one million lookups well under a second`() {
        val list = DomainBlocklist.of(domains)
        repeat(10_000) { list.matches("warmup$it.com") }
        var hits = 0
        // Meilleure de 3 mesures : une machine chargée (émulateur, compilation) ne fait pas échouer le test.
        val ns = (1..3).minOf {
            measureNanoTime {
                for (i in 0 until 1_000_000) if (list.matches("www.site-${i % 400_000}.example${i % 50}.com")) hits++
            }
        }
        assertThat(hits).isGreaterThan(0)
        val perLookupUs = ns / 1e3 / 1_000_000
        assertThat(perLookupUs).isLessThan(5.0) // < 5 µs par requête sur PC
    }

    @Test fun `text check of a long page stays cheap`() {
        val policy = ContentPolicy(DomainBlocklist.of(domains))
        val page = buildString { repeat(2_000) { append("Lorem ipsum dolor sit amet, voir www.news$it.com. ") } }
        repeat(5) { policy.checkText(page) }
        val ms = (1..5).minOf { measureNanoTime { repeat(4) { policy.checkText(page) } } } / 1e6 / 4
        assertThat(ms).isLessThan(50.0)
    }

    @Test fun `streak math`() {
        val s = Streak(LocalDate.of(2026, 1, 1))
        assertThat(s.currentDays(LocalDate.of(2026, 1, 31))).isEqualTo(30)
        val r = s.relapse(LocalDate.of(2026, 1, 31))
        assertThat(r.bestDays).isEqualTo(30)
        assertThat(r.currentDays(LocalDate.of(2026, 1, 31))).isEqualTo(0)
        assertThat(r.relapses).isEqualTo(1)
        assertThat(s.nextMilestone(LocalDate.of(2026, 1, 5))).isEqualTo(7)
    }
}
