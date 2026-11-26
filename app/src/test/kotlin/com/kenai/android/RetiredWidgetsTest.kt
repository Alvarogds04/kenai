package com.kenai.android

import com.kenai.android.core.RetiredWidgets
import com.kenai.android.core.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app tells a user who had one of the retired Widgets (KN-13 S3, L6): each one by the name the picker used, and where its
 * function lives now, in both languages. Nothing is dropped silently.
 */
class RetiredWidgetsTest {
    private val both = listOf(Words.EN, Words.ES)

    @Test fun everyRetiredWidgetIsNamedAndSaysWhereItWent() {
        for (w in both) {
            val entries = RetiredWidgets.of(w).entries
            assertEquals("four retired: List, Alert, Today and Mood", 4, entries.size)
            for (e in entries) {
                assertTrue("a name", e.name.isNotBlank())
                assertTrue("where it went, for ${e.name}", e.where.isNotBlank())
                assertTrue("${e.name} must say it lives in the new Widget now: ${e.where}", e.where.contains(com.kenai.android.core.ActivityWords.of(w).title))
                assertTrue("${e.name} is one short line", e.where.length <= 90)
            }
        }
    }

    @Test fun theNamesAreTheOnesThePickerUsed() {
        assertEquals(
            listOf("All your agents", "Approvals waiting", "Finished today", "Agents' mood"),
            RetiredWidgets.of(Words.EN).entries.map { it.name },
        )
        assertEquals(
            listOf("Todos tus agentes", "Aprobaciones pendientes", "Terminadas hoy", "Ánimo de los agentes"),
            RetiredWidgets.of(Words.ES).entries.map { it.name },
        )
    }

    @Test fun theFinishedTodayCountIsExplainedAsApproximateAndPhoneOnly() {
        val today = RetiredWidgets.of(Words.EN).entries.single { it.name == "Finished today" }
        assertTrue(today.where, today.where.contains("approx."))
        val hoy = RetiredWidgets.of(Words.ES).entries.single { it.name == "Terminadas hoy" }
        assertTrue(hoy.where, hoy.where.contains("aprox."))
    }

    @Test fun theSpanishIsSpanishAndNotTheEnglishText() {
        val en = RetiredWidgets.of(Words.EN).entries.map { it.where }
        val es = RetiredWidgets.of(Words.ES).entries.map { it.where }
        assertEquals(4, es.size)
        for (i in es.indices) assertFalse("entry $i is not translated", en[i] == es[i])
        assertTrue(es.any { it.contains("ahora") })
    }

    @Test fun itMeetsTheAskEveryWidgetSourceMustMeet() {
        // The same guard every Widget source has (WidgetSurfacesTest): names and one plain line, nothing else.
        val src = java.io.File("src/main/kotlin/com/kenai/android/core/RetiredWidgets.kt").readText()
        for (word in listOf("statusText", "finalLine", "steps", ".command", "files", "project")) assertFalse("$word", src.contains(word))
        for (word in listOf("decide", "allow", "deny", "approve", "answer", "unpair")) assertFalse(word, src.contains(word, ignoreCase = true))
    }
}
