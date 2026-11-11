package com.kenai.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Gallery names every state and emote with words of the phone's language, never the internal keys. */
class GalleryLabelsTest {
    private val main = File("src/main/kotlin/com/kenai/android/MainActivity.kt").readText()
    private val emotes = listOf("love", "surprised", "proud", "wink", "yawn", "happy", "annoyed")

    @Test fun theGalleryShowsTranslatedNamesNotKeys() {
        assertFalse("a state's internal key is shown", main.contains("GalleryCell(s.key"))
        assertFalse("an emote's internal key is shown", main.contains("GalleryCell(m.key"))
        assertTrue(main.contains("stringResource(stateLabel(s))"))
        assertTrue(main.contains("stringResource(emoteLabel(m))"))
    }

    @Test fun everyEmoteHasANameInEnglishAndSpanish() {
        for (path in listOf("src/main/res/values/strings.xml", "src/main/res/values-es/strings.xml")) {
            val xml = File(path).readText()
            for (e in emotes) assertTrue("$path lacks emote_$e", xml.contains("<string name=\"emote_$e\">"))
        }
    }
}
