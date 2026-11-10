package com.kenai.android

import com.kenai.android.character.StateColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetsTest {
    private val raw = File("src/main/res/raw")
    private val res = File("src/main/res")

    @Test fun everySoundTheAppPlaysIsBundled() {
        val code = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val played = Regex("""\.play\("(\w+)"\)""").findAll(code).map { it.groupValues[1] }.toSet() + StateColors.STATE_SOUND.values
        assertTrue(played.isNotEmpty())
        // Kenai ships no sounds yet (HU-049 T3): the player's table is empty, so every name is silently ignored.
        // Once sounds are added, every name played must be bundled.
        if (raw.exists()) played.forEach { assertTrue("$it.wav", File(raw, "$it.wav").exists()) }
    }

    @Test fun noBundledSoundIsLeftUnused() {
        val code = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" && it.name != "SoundPlayer.kt" }.joinToString("\n") { it.readText() }
        val played = Regex("""\.play\("(\w+)"\)""").findAll(code).map { it.groupValues[1] }.toSet() + StateColors.STATE_SOUND.values
        val bundled = raw.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: return
        assertEquals(bundled, played)
    }


    @Test fun rawNamesAreValidAndroidResourceNames() {
        raw.listFiles()?.forEach { assertTrue(it.name, Regex("""[a-z][a-z0-9_]*\.wav""").matches(it.name)) }
    }

    @Test fun soundTableCoversEveryBundledSound() {
        val src = File("src/main/kotlin/com/kenai/android/sound/SoundPlayer.kt").readText()
        val mapped = Regex("\"(\\w+)\" to R\\.raw\\.(\\w+)").findAll(src).map { assertEquals(it.groupValues[1], it.groupValues[2]); it.groupValues[1] }.toSet()
        assertEquals(raw.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet<String>(), mapped)
    }

    @Test fun notificationIconIsMonochromeVector() {
        assertTrue(File(res, "drawable/ic_stat_kenai.xml").readText().contains("#FFFFFFFF"))
    }

    @Test fun launcherIconIsPresent() {
        assertTrue(File(res, "mipmap-anydpi-v26/ic_launcher.xml").readText().contains("@drawable/ic_launcher_fg"))
        assertTrue(File(res, "drawable/ic_launcher_fg.xml").exists())
    }
}
