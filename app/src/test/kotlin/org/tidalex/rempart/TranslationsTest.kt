package org.tidalex.rempart

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Rempart suit la langue du téléphone : chaque texte doit exister dans TOUTES
 * les langues livrées, sinon une phrase anglaise apparaîtrait au milieu du français.
 */
class TranslationsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
    private val modules = listOf("app", "lock", "filter-dns", "filter-accessibility")
    private val languages = listOf("fr")

    private fun keys(f: File): Map<String, String> {
        if (!f.exists()) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = mutableMapOf<String, String>()
        for (tag in listOf("string", "string-array", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as org.w3c.dom.Element
                if (e.getAttribute("translatable") != "false") out[e.getAttribute("name")] = e.textContent
            }
        }
        return out
    }

    @Test fun `every string exists in every shipped language`() {
        for (m in modules) {
            val base = keys(File(root, "$m/src/main/res/values/strings.xml"))
            assertThat(base).isNotEmpty()
            for (lang in languages) {
                val tr = keys(File(root, "$m/src/main/res/values-$lang/strings.xml"))
                assertWithMessage("$m [$lang]").that(tr.keys).containsExactlyElementsIn(base.keys)
            }
        }
    }

    @Test fun `format placeholders match between languages`() {
        val ph = Regex("%\\d\\$[sd]")
        for (m in modules) {
            val base = keys(File(root, "$m/src/main/res/values/strings.xml"))
            for (lang in languages) {
                val tr = keys(File(root, "$m/src/main/res/values-$lang/strings.xml"))
                base.forEach { (k, v) ->
                    assertWithMessage("$m/$lang/$k").that(ph.findAll(tr[k]!!).map { it.value }.toSet()).isEqualTo(ph.findAll(v).map { it.value }.toSet())
                }
            }
        }
    }

    /** Règle d'écriture du projet : pas de tiret long ni moyen comme ponctuation. */
    @Test fun `no long dashes in any displayed text`() {
        for (m in modules) {
            for (dir in listOf("values") + languages.map { "values-$it" }) {
                keys(File(root, "$m/src/main/res/$dir/strings.xml")).forEach { (k, v) ->
                    assertWithMessage("$m/$dir/$k").that(v).doesNotContainMatch("[—–]")
                }
            }
        }
    }

    @Test fun `every integrity event has a label`() {
        val base = keys(File(root, "app/src/main/res/values/strings.xml"))
        org.tidalex.rempart.core.integrity.IntegrityEvent.entries.forEach { assertThat(base).containsKey("ev_${it.name}") }
    }

    @Test fun `locale config lists the shipped languages`() {
        val cfg = File(root, "app/src/main/res/xml/locales_config.xml").readText()
        (listOf("en") + languages).forEach { assertThat(cfg).contains("android:name=\"$it\"") }
    }
}
