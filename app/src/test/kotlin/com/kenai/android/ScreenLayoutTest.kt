package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for where things live on screen (the Compose code cannot be rendered in a JVM test, so
 * these read the sources). Home is the agent card; Settings holds the rest, including the notice
 * that credits the MIT code taken from Coucou for Android, which must stay reachable and visible.
 */
class ScreenLayoutTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val main get() = src("MainActivity.kt")
    private val settings get() = src("ui/SettingsScreens.kt")
    private val homePanel get() = src("ui/HomePanel.kt")

    @Test fun settingsHoldsDisconnectGalleryOverlayAndTheRequiredNotice() {
        for (s in listOf(
            "R.string.action_unpair", "R.string.demo_leave", "R.string.gallery", "R.string.overlay_title",
            "R.string.about_unofficial", "R.string.about_repo", "R.string.about_assets", "R.string.settings_section_about",
        )) assertTrue("Settings must carry $s", settings.contains(s))
    }

    @Test fun theNoticeNamesTheOriginalAuthorAndHisRepository() {
        val xml = File("src/main/res/values/strings.xml").readText()
        assertTrue(xml.contains("comes from Coucou for Android by Rhytam Biswas"))
        assertTrue(xml.contains("github.com/Alvarogds04/kenai"))
        assertTrue(xml.contains("does not use Coucou"))
        assertTrue(xml.contains("MIT License"))
    }

    @Test fun theHeroNeverShowsTheRawStatusText() {
        assertTrue(homePanel.contains("HomeText.line("))
        assertFalse("the raw text must not be drawn directly", Regex("""Text\(\s*(focus\??\.|detail = focus)?statusText""").containsMatchIn(homePanel))
    }

    @Test fun activityShowsSentencesNeverTheCommand() {
        val history = settings.substringAfter("fun HistoryScreen")
        assertTrue(history.contains("HomeText.decision("))
        assertFalse("no raw command or tool: command line", history.contains("d.command") || history.contains("Monospace"))
    }

    /** kenai-0.1 S9: the request hero, the strip and the sections say what agents do in sentences, never the raw status text. */
    @Test fun theNewHomePartsNeverShowTheRawStatusText() {
        for (f in listOf("ui/RequestHero.kt", "ui/StatusStrip.kt", "ui/HomePanel.kt")) {
            val text = src(f)
            assertFalse("$f draws statusText", Regex("""Text\(\s*[\w.?]*statusText""").containsMatchIn(text))
            assertFalse("$f reads statusText", text.contains(".statusText"))
        }
        assertTrue(homePanel.contains("HomeText.line("))
        assertTrue("the request names the action", src("ui/RequestHero.kt").contains("HomeText.wants("))
    }

    /** kenai-0.1 S9: Inicio's three states, each from the pure rule, and the strip on top of every one. */
    @Test fun homeIsBuiltFromItsScenes() {
        val home = main.substringAfter("private fun Home(")
        assertTrue(home.contains("HomePanel.scene("))
        for (scene in listOf("Scene.REQUEST", "Scene.WAITING", "Scene.CALM", "Scene.OFFLINE")) assertTrue(scene, home.contains(scene))
        assertTrue(home.contains("RequestHero(") && home.contains("CalmHero(") && home.contains("OfflineHero(") && home.contains("HeroCard("))
        assertTrue(home.contains("StatusStrip("))
        assertTrue(home.contains("HomePanel.groups("))
        assertTrue("the offline card replaces the link panel and the discovery hint", !home.contains("LinkHealthPanel(") && !home.contains("DiscoveryHint("))
        assertTrue("Volver a probar reconnects now", home.contains("model.retryNow()"))
        assertTrue(src("app/AppModel.kt").contains("fun retryNow()"))
    }
}
