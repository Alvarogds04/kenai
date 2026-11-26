package com.kenai.android

import com.kenai.android.core.RetiredWidgets
import com.kenai.android.core.Words
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3 in the app itself (KN-13 L6): whoever had one of the retired Widgets reads in Settings where it went, without any prior state, in the
 * phone's language. The Compose code cannot be rendered in a JVM test, so this reads the source the way [ScreenLayoutTest] does.
 */
class WidgetsSettingsTest {
    private val settings = File("src/main/kotlin/com/kenai/android/ui/SettingsScreens.kt").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()
    private val spanish = File("src/main/res/values-es/strings.xml").readText()

    @Test fun settingsHasAWidgetsSectionThatNeedsNoPriorState() {
        assertTrue("Settings has no Widgets section", settings.contains("R.string.settings_section_widgets"))
        // A section of the list, always there: no flag, no "seen" state, nothing that has to happen first.
        val section = settings.substringAfter("R.string.settings_section_widgets").substringBefore("R.string.settings_section_more")
        assertTrue("the section is empty", section.contains("RetiredWidgets.of("))
        for (state in listOf("model.batteryHintVisible", "rememberSaveable", "remember {", "seen", "dismiss")) {
            assertFalse("the section waits for $state", section.contains(state))
        }
    }

    @Test fun everyRetiredWidgetAndWhereItWentIsListedFromCore() {
        // One place for the words (core/RetiredWidgets), so the list cannot drift from the tests that pin it.
        assertTrue(settings.contains("RetiredWidgets.of("))
        assertTrue(settings.contains("Words.current()"))
        assertTrue(settings.contains("retired.name") && settings.contains("retired.where"))
        val en = RetiredWidgets.of(Words.EN).entries
        assertEquals("List, Alert, Today and Mood", 4, en.size)
        assertEquals(en.size, RetiredWidgets.of(Words.ES).entries.size)
    }

    @Test fun theSectionHasItsHeadingAndOneLineOfExplanationInBothLanguages() {
        for ((tag, file) in listOf("en" to strings, "es" to spanish)) {
            assertTrue("$tag: no heading", file.contains("""name="settings_section_widgets""""))
            assertTrue("$tag: no heading text", file.contains(">Widgets<"))
            assertTrue("$tag: no explanation", file.contains("""name="settings_widgets_intro""""))
        }
        // The explanation says what happened, rather than leaving a bare list of names.
        assertTrue(strings.contains("retired"))
        assertTrue(spanish.contains("retiraron") || spanish.contains("retirado"))
    }
}
