package dev.mothlight.plantagotchi

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every language must have the same string and plural keys as English, with the same placeholders. */
class StringsTest {
    private val translations = listOf("values-de", "values-fr", "values-es")

    /** Host tests run in the `shared` module dir; the second candidate covers runs from the project root. */
    private fun resourcesDir(): Path =
        listOf("src/commonMain/composeResources", "shared/src/commonMain/composeResources")
            .map { Path(it) }
            .firstOrNull { SystemFileSystem.exists(Path(it, "values", "strings.xml")) }
            ?: fail("composeResources not found from the working directory")

    /** Placeholders per string key, and per plural key and quantity. */
    private class Strings(val strings: Map<String, Set<String>>, val plurals: Map<String, Map<String, Set<String>>>)

    private fun load(dir: String): Strings {
        val xml = SystemFileSystem.source(Path(resourcesDir(), dir, "strings.xml")).buffered().use { it.readString() }
        val strings = STRING.findAll(xml).associate { it.groupValues[1] to placeholders(it.groupValues[2]) }
        val plurals = PLURALS.findAll(xml).associate { m ->
            val items = ITEM.findAll(m.groupValues[2]).associate { it.groupValues[1] to placeholders(it.groupValues[2]) }
            assertTrue("other" in items, "$dir: plurals ${m.groupValues[1]} has no 'other' item")
            m.groupValues[1] to items
        }
        return Strings(strings, plurals)
    }

    private fun placeholders(text: String): Set<String> = PLACEHOLDER.findAll(text).map { it.value }.toSet()

    private fun assertSameKeys(what: String, expected: Set<String>, actual: Set<String>) {
        val missing = (expected - actual).sorted()
        val extra = (actual - expected).sorted()
        assertTrue(missing.isEmpty() && extra.isEmpty(), "$what: missing $missing, extra $extra")
    }

    @Test
    fun allLanguagesHaveTheSameKeys() {
        val english = load("values")
        assertTrue(english.strings.size > 100, "suspiciously few strings: ${english.strings.size}")
        for (dir in translations) {
            val other = load(dir)
            assertSameKeys("$dir strings", english.strings.keys, other.strings.keys)
            assertSameKeys("$dir plurals", english.plurals.keys, other.plurals.keys)
        }
    }

    @Test
    fun allLanguagesHaveTheSamePlaceholders() {
        val english = load("values")
        for (dir in translations) {
            val other = load(dir)
            for ((key, expected) in english.strings) {
                val actual = other.strings[key] ?: continue
                assertEquals(expected, actual, "$dir: placeholders of string $key")
            }
            // Quantities differ per language; "other" exists everywhere and must carry all placeholders.
            for ((key, items) in english.plurals) {
                val actual = other.plurals[key] ?: continue
                assertEquals(items.getValue("other"), actual.getValue("other"), "$dir: placeholders of plurals $key (other)")
                assertEquals(items.values.flatten().toSet(), actual.values.flatten().toSet(), "$dir: placeholders of plurals $key")
            }
        }
    }

    private companion object {
        val STRING = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val PLURALS = Regex("""<plurals\s+name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        val ITEM = Regex("""<item\s+quantity="([^"]+)"\s*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
        val PLACEHOLDER = Regex("""%\d+\$[sd]""")
    }
}
