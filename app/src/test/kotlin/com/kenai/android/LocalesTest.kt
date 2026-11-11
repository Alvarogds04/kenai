package com.kenai.android

import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The app ships English (the default, in values/) and Spanish (values-es/). Android picks Spanish when the phone is in Spanish and
 * English for every other language; there is no per-app language picker. Both resource files are generated from i18n/.
 */
class LocalesTest {
    private fun read(path: String) = File(path).readText()

    /** Language tag → (JSON source, generated resource file). English is the default and lives in values/. */
    private val locales = mapOf(
        "en" to ("../i18n/app-strings.json" to "src/main/res/values/strings.xml"),
        "es" to ("../i18n/app-strings.es.json" to "src/main/res/values-es/strings.xml"),
    )

    private fun source(path: String): Map<String, String> {
        val strings = JSONObject(read(path)).getJSONObject("strings")
        return strings.keys().asSequence().associateWith { strings.getString(it) }
    }

    private fun resources(path: String): Map<String, String> =
        Regex("""<string name="([a-z_0-9]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(read(path))
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** The format tokens of a string, in a stable order: positional arguments (%1$s, %2$d) and literal %%. */
    private fun placeholders(text: String): List<String> = Regex("""%(\d+\$)?[a-zA-Z%]""").findAll(text).map { it.value }.sorted().toList()

    @Test fun theBuildKeepsEnglishAndSpanishResources() {
        val gradle = read("build.gradle.kts")
        assertTrue(Regex("""resourceConfigurations\s*\+=\s*listOf\("en",\s*"es"\)""").containsMatchIn(gradle))
    }

    @Test fun thereIsNoPerAppLanguagePicker() {
        assertFalse(read("src/main/AndroidManifest.xml").contains("localeConfig"))
        assertFalse(File("src/main/res/xml/locales_config.xml").exists())
        assertFalse(File("src/main/kotlin/com/kenai/android/core/Languages.kt").exists())
        val settings = read("src/main/kotlin/com/kenai/android/ui/SettingsScreens.kt")
        assertFalse(settings.contains("LocaleManager"))
        assertFalse(settings.contains("applicationLocales"))
    }

    @Test fun everyLocaleSourceHasExactlyTheEnglishKeys() {
        val english = source(locales.getValue("en").first).keys
        for ((tag, files) in locales) assertEquals("keys of ${files.first} ($tag)", english, source(files.first).keys)
    }

    @Test fun everyGeneratedFileHasExactlyItsSourceKeys() {
        for ((tag, files) in locales) {
            assertTrue("missing ${files.second} ($tag)", File(files.second).exists())
            assertEquals("keys of ${files.second} ($tag)", source(files.first).keys, resources(files.second).keys)
        }
    }

    @Test fun everyTranslationKeepsTheEnglishPlaceholders() {
        val english = source(locales.getValue("en").first)
        val englishRes = resources(locales.getValue("en").second)
        for ((tag, files) in locales) {
            val translated = source(files.first)
            val translatedRes = resources(files.second)
            for ((key, text) in english) {
                assertEquals("placeholders of $key in ${files.first} ($tag)", placeholders(text), placeholders(translated[key].orEmpty()))
                assertEquals("placeholders of $key in ${files.second} ($tag)", placeholders(englishRes[key].orEmpty()), placeholders(translatedRes[key].orEmpty()))
            }
        }
    }

    @Test fun everyTranslationIsFilledIn() {
        for ((tag, files) in locales) {
            val blank = source(files.first).filterValues { it.isBlank() }.keys
            assertTrue("blank strings in $tag: $blank", blank.isEmpty())
        }
    }

    @Test fun theOnlyLanguagesAreEnglishAndSpanish() {
        val folders = File("src/main/res").listFiles().orEmpty().map { it.name }.filter { it.startsWith("values") }
        // values-notnight is not a language: it is the light variant of the Widgets' colours, and holds colours only.
        assertEquals(listOf("values", "values-es", "values-notnight"), folders.sorted())
        assertEquals(listOf("colors.xml"), File("src/main/res/values-notnight").listFiles().orEmpty().map { it.name })
        assertEquals(listOf("strings.xml"), File("src/main/res/values-es").listFiles().orEmpty().map { it.name })
        assertEquals(listOf("app-strings.es.json", "app-strings.json"), File("../i18n").listFiles().orEmpty().map { it.name }.sorted())
    }

    @Test fun theGeneratedFilesAreUpToDate() {
        val haveNode = runCatching {
            ProcessBuilder("node", "--version").redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0
        }.getOrDefault(false)
        assumeTrue("node not available", haveNode)
        val proc = ProcessBuilder("node", "../scripts/gen-strings.mjs", "--check").redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        assertTrue("gen-strings timed out", proc.waitFor(60, TimeUnit.SECONDS))
        assertEquals("gen-strings --check: $output", 0, proc.exitValue())
    }
}
