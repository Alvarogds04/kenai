package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source guards for the Solo and List widgets (Compose and Android classes only compile in CI), in the style of [GlanceSurfacesTest]. */
class WidgetSurfacesTest {
    private val main = "src/main/kotlin/com/kenai/android/"
    private fun read(path: String) = File(path).readText()
    private val manifest get() = read("src/main/AndroidManifest.xml")

    /** Every widget source: the providers (retired shells included), the hub, the pictures, the pure logic and the day's count. */
    private val widgets = listOf(
        "SoloWidget" to "kenai_solo_widget_info", "ListWidget" to "kenai_list_widget_info", "CharacterBigWidget" to "kenai_big_widget_info",
        "StatusPillWidget" to "kenai_pill_widget_info", "WaitingAlertWidget" to "kenai_alert_widget_info",
        "UsageRingWidget" to "kenai_ring_widget_info", "TodayWidget" to "kenai_today_widget_info", "MoodWidget" to "kenai_mood_widget_info",
        // «Sesiones y actividad» (KN-13): the redesign in place of the old summary, same class, receiver and provider file.
        "GlanceWidget" to "kenai_widget_info",
    )
    private val sources = (
        widgets.map { "app/${it.first}.kt" } +
            listOf(
                "app/WidgetHub.kt", "app/CharacterBitmap.kt", "app/RingBitmap.kt", "app/BarBitmap.kt", "app/RetiredWidgetCard.kt", "app/TodayStore.kt",
                "core/WidgetData.kt", "core/ActivityPlan.kt", "core/RetiredWidgets.kt", "core/TodayCounter.kt", "core/LaunchScreen.kt",
            )
        ).associateWith { read(main + it) }

    @Test fun theWidgetsAreRegisteredAndNeverWakeOnATimer() {
        for ((cls, info) in widgets) {
            assertTrue(manifest.contains("""android:name=".app.$cls""""))
            assertTrue(manifest.contains("@xml/$info"))
            val xml = read("src/main/res/xml/$info.xml")
            assertTrue(xml.contains("""android:updatePeriodMillis="0""""))
            assertTrue(xml.contains("""android:widgetCategory="home_screen""""))
        }
    }

    @Test fun noWidgetSourceTouchesAnythingBeyondTheGlance() {
        // Agent names, state words and colours only: no step text, no command, no file, no answer.
        for ((name, src) in sources) {
            for (word in listOf("statusText", "finalLine", "steps", ".command", "files", "project")) assertFalse("$name uses $word", src.contains(word))
        }
    }

    @Test fun noWidgetCanDecideAnything() {
        for ((name, src) in sources) {
            for (word in listOf("decide", "allow", "deny", "approve", "answer", "unpair")) assertFalse("$name uses $word", src.contains(word, ignoreCase = true))
        }
        for (layout in listOf("widget_solo", "widget_glance")) assertFalse(read("src/main/res/layout/$layout.xml").contains("Button"))
    }

    @Test fun noTimerAndNoLibrary() {
        for ((name, src) in sources) {
            for (word in listOf("WorkManager", "postDelayed", "Handler(", "Timer", "androidx.glance")) assertFalse("$name uses $word", src.contains(word))
            if (name != "app/WidgetHub.kt") assertFalse("$name uses AlarmManager", src.contains("AlarmManager"))
        }
        // The one alarm: the Today widget at local midnight. Inexact and not waking the phone, so no exact-alarm permission and no wake-ups.
        val hub = sources.getValue("app/WidgetHub.kt")
        assertEquals(1, Regex("alarms\\.set\\(").findAll(hub).count())
        assertTrue(hub.contains("alarms.set(AlarmManager.RTC,"))
        for (word in listOf("RTC_WAKEUP", "setExact", "setAlarmClock", "setRepeating", "setInexactRepeating", "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM")) {
            assertFalse(word, hub.contains(word))
            assertFalse("manifest asks for $word", manifest.contains(word))
        }
        assertFalse(read("build.gradle.kts").contains("androidx.glance"))
    }

    @Test fun everyPendingIntentIsImmutableAndExplicit() {
        val hub = sources.getValue("app/WidgetHub.kt")
        // Open the app, open a session, open a screen: three, all immutable, the last two through LaunchActivity.
        assertEquals(3, Regex("PendingIntent.getActivity").findAll(hub).count())
        // Plus the midnight alarm: one explicit, immutable broadcast to «Sesiones y actividad».
        assertEquals(1, Regex("PendingIntent.getBroadcast").findAll(hub).count())
        assertTrue(hub.contains("Intent(ACTION_MIDNIGHT).setClass(context, GlanceWidget::class.java)"))
        assertEquals(4, Regex("FLAG_IMMUTABLE").findAll(hub).count())
        assertEquals(2, Regex("Intent\\(context, LaunchActivity::class.java\\)").findAll(hub).count())
        assertTrue(hub.contains("Intent(context, MainActivity::class.java)"))
        for ((name, src) in sources) {
            if (name == "app/WidgetHub.kt") continue
            assertFalse("$name builds its own PendingIntent", src.contains("PendingIntent.get"))
            assertFalse("$name builds its own Intent", src.contains("Intent(\""))
        }
    }

    @Test fun everyRowAndEveryWidgetHasItsOwnRequestCode() {
        val hub = sources.getValue("app/WidgetHub.kt")
        val codes = Regex("""const val CODE_\w+ = (0x[0-9A-Fa-f]+)""").findAll(hub).map { it.groupValues[1] }.toList()
        assertEquals(14, codes.size)
        assertEquals(codes.size, codes.toSet().size)
        // The rows use CODE_GLANCE_ROW + index: the ranges must not run into the next code.
        val values = codes.map { it.removePrefix("0x").toInt(16) }.sorted()
        assertTrue(values.zipWithNext().all { (a, b) -> b - a >= 16 })
        assertTrue(sources.getValue("app/GlanceWidget.kt").contains("WidgetHub.CODE_GLANCE_ROW + i"))
        // Every live provider uses its own code, and the family uses each code once. The two shells merged into «Sesiones y actividad»
        // (KN-13 L6) draw its picture, so they use its codes instead of keeping dead ones of their own.
        val live = widgets.map { it.first }.filter { it != "ListWidget" && it != "WaitingAlertWidget" }
        val used = live.map { cls -> Regex("""WidgetHub\.(CODE_\w+)""").findAll(sources.getValue("app/$cls.kt")).map { it.groupValues[1] }.toSet() }
        for (u in used) assertTrue(u.isNotEmpty())
        val flat = used.flatten()
        assertEquals("a code is shared by two Widgets: $flat", flat.size, flat.toSet().size)
        for (shell in listOf("ListWidget", "WaitingAlertWidget")) {
            assertTrue("$shell renders the new card", sources.getValue("app/$shell.kt").contains("GlanceWidget.views(context, c)"))
        }
    }

    @Test fun theImageAndTheBadgeAreDescribed() {
        assertTrue(sources.getValue("app/SoloWidget.kt").contains("setContentDescription(R.id.solo_character"))
        assertTrue(sources.getValue("app/SoloWidget.kt").contains("setContentDescription(R.id.solo_badge"))
        assertTrue(sources.getValue("app/GlanceWidget.kt").contains("setContentDescription(R.id.glance_badge"))
        // The retired shells draw through the new card or through the small retired card, so their pictures are described there.
        assertTrue(sources.getValue("app/RetiredWidgetCard.kt").contains("setContentDescription(R.id.retired_face"))
        for ((cls, _) in widgets) {
            val src = sources.getValue("app/$cls.kt")
            assertTrue(
                "$cls has no content description, here or in the card it draws",
                src.contains("setContentDescription(") || src.contains("GlanceWidget.views(context, c)") || src.contains("RetiredWidgetCard.views("),
            )
        }
        for (layout in File("src/main/res/layout").listFiles()!!.filter { it.name.startsWith("widget_") }) {
            for (img in Regex("""<ImageView[^>]*>""").findAll(layout.readText())) {
                val tag = img.value
                assertTrue("${layout.name}: an image with neither a description nor importantForAccessibility=no: $tag",
                    tag.contains("android:contentDescription") || tag.contains("""android:importantForAccessibility="no""""))
            }
        }
    }

    @Test fun everyChangeOfThePictureReachesTheHub() {
        val model = read(main + "app/AppModel.kt")
        val push = model.substringAfter("private fun pushGlance()").substringBefore("private fun refreshIsland()")
        assertTrue(push.contains("WidgetHub.update"))
        // One push for every Widget: «Sesiones y actividad» goes through the hub like the rest, with no push of its own (KN-13 L5).
        assertFalse("the old summary push is gone", push.contains("GlanceWidget.update"))
        assertTrue(sources.getValue("app/WidgetHub.kt").contains("if (content == last) return"))
        // And the hub reaches every kind, the retired shells included.
        val hub = sources.getValue("app/WidgetHub.kt")
        for ((cls, _) in widgets) assertTrue("the hub never draws $cls", hub.contains("Kind($cls::class.java)"))
    }

    @Test fun theActivityWidgetKeepsItsReceiverAndItsProviderAndNowDrawsTheSessions() {
        // The redesign in place (KN-13 L5): same names, same provider file, so the instances already placed survive.
        assertTrue(manifest.contains("""android:name=".app.GlanceWidget""""))
        assertTrue(manifest.contains("@xml/kenai_widget_info"))
        assertTrue(manifest.contains("""android:label="@string/widget_activity_label""""))
        assertTrue(File("src/main/res/layout/widget_glance.xml").exists())
        val info = read("src/main/res/xml/kenai_widget_info.xml")
        assertTrue(info.contains("""android:targetCellWidth="4""""))
        assertTrue(info.contains("""android:targetCellHeight="3""""))
        assertTrue(info.contains("""android:initialLayout="@layout/widget_glance""""))
        val src = read(main + "app/GlanceWidget.kt")
        // Its data is the Widgets' content, and a row opens its agent; a tap anywhere else opens the app.
        assertTrue(src.contains("fun views(context: Context, c: WidgetContent)"))
        assertTrue(src.contains("ActivityPlan.of("))
        assertTrue(src.contains("WidgetHub.openApp(") && src.contains("WidgetHub.CODE_GLANCE"))
        assertTrue(src.contains("WidgetHub.openSession(context, WidgetHub.CODE_GLANCE_ROW + i, row.pillId)"))
        // The pending button opens Home and nothing else, through the closed list of screens.
        assertTrue(src.contains("WidgetHub.openScreen(context, WidgetHub.CODE_GLANCE_BUTTON, LaunchScreen.HOME)"))
        // The old summary push is gone: the hub is the one way in.
        assertFalse(src.contains("fun update(context: Context, glance: Glance)"))
        val layout = read("src/main/res/layout/widget_glance.xml")
        assertFalse(layout.contains("<Button"))
        assertFalse("no icon button in the activity card", Regex("ic_widget_(home|chat|settings|recent|session|pair)").containsMatchIn(layout))
        assertTrue("it shows the not-connected icon", layout.contains("@drawable/ic_widget_offline"))
    }

    @Test fun theRowsAreAtLeastFortyEightDpAndTheEmptyStateFillsTheCard() {
        val glance = read("src/main/res/layout/widget_glance.xml")
        // Five session rows, the same 48dp minimum every Widget row has.
        assertEquals(5, Regex("""android:layout_height="@dimen/widget_row_min"""").findAll(glance).count())
        for (id in listOf("glance_empty", "glance_empty_face", "glance_empty_title", "glance_empty_line", "glance_tint", "widget_root")) assertTrue(id, glance.contains("@+id/$id"))
        // One row per agent: the dot of its tone, its name, its state word and room for the engine's counter.
        for (i in 0 until 5) for (part in listOf("row", "dot", "name", "state", "progress")) assertTrue("glance_${part}$i", glance.contains("@+id/glance_${part}$i"))
        // The pending section holds three rows and its button.
        for (i in 0 until 3) for (part in listOf("row", "dot", "name", "word")) assertTrue("glance_pending_${part}$i", glance.contains("@+id/glance_pending_${part}$i"))
        for (id in listOf("glance_pending", "glance_review", "glance_today", "glance_badge")) assertTrue(id, glance.contains("@+id/$id"))
        val code = read(main + "app/GlanceWidget.kt")
        assertTrue(code.contains("plan.rows.isEmpty()"))
        assertTrue(code.contains("R.id.glance_empty"))
    }

    @Test fun theCardsUseTheSharedLook() {
        for (layout in listOf("widget_solo", "widget_glance", "widget_pill", "widget_big")) {
            val xml = read("src/main/res/layout/$layout.xml")
            assertTrue(layout, xml.contains("@drawable/widget_card"))
            assertTrue(layout, xml.contains("@drawable/widget_tint_muted"))
            // Colours and sizes come from the shared resources, never from a literal grey at 12sp.
            assertFalse(layout, xml.contains("#FFAEAEB2"))
        }
        val solo = read(main + "app/SoloWidget.kt")
        assertTrue(solo.contains("WidgetHub.paint("))
        assertTrue(read(main + "app/GlanceWidget.kt").contains("WidgetHub.paint("))
    }

    @Test fun characterHasAtLeastHalfOfTheSoloCard() {
        val solo = read("src/main/res/layout/widget_solo.xml")
        // Kenai takes the leftover height (weight 1) while the text rows wrap their content.
        assertTrue(solo.contains("""android:layout_weight="1""""))
        assertTrue(read(main + "app/SoloWidget.kt").contains("glow = true"))
    }

    // ── The debug preview: it must exist in debug builds and nowhere else ──────────────────────────

    @Test fun thePreviewReceiverLivesInTheDebugSourceSetOnly() {
        assertTrue(File("src/debug/kotlin/com/kenai/android/app/WidgetPreviewReceiver.kt").exists())
        val debugManifest = read("src/debug/AndroidManifest.xml")
        assertTrue(debugManifest.contains(".app.WidgetPreviewReceiver"))
        assertTrue(debugManifest.contains("com.kenai.android.debug.WIDGET_STATE"))
        // Not in the main source set, not in the manifest every build gets.
        assertFalse(File(main + "app/WidgetPreviewReceiver.kt").exists())
        assertFalse(manifest.contains("WidgetPreviewReceiver"))
        assertFalse(manifest.contains("com.kenai.android.debug"))
        assertFalse(File("src/release").exists() && File("src/release").walkTopDown().any { it.readText().contains("WidgetPreviewReceiver") })
        // Nothing in the main code calls it, and nothing outside the debug receiver pins a preview.
        for (f in File(main).walkTopDown().filter { it.extension == "kt" }) {
            val text = f.readText()
            assertFalse("${f.name} refers to the preview receiver", text.contains("WidgetPreviewReceiver") && f.name != "WidgetHub.kt")
            if (f.name != "WidgetHub.kt") assertFalse("${f.name} pins a preview", text.contains("WidgetHub.preview("))
        }
        assertTrue(read("build.gradle.kts").contains("""sourceSets["debug"]"""))
    }

    @Test fun thePreviewReceiverDocumentsEveryCommand() {
        val src = read("src/debug/kotlin/com/kenai/android/app/WidgetPreviewReceiver.kt")
        for (state in listOf("disconnected", "empty", "working", "waiting", "done", "live")) {
            assertTrue(state, src.contains("--es state $state"))
        }
        assertTrue(src.contains("adb shell am broadcast -n com.kenai.android/.app.WidgetPreviewReceiver -a com.kenai.android.debug.WIDGET_STATE"))
        assertTrue(src.contains("DEBUG BUILDS ONLY"))
        assertTrue(src.contains("--ei usage 92"))
        // It only redraws Widgets with a made-up state.
        assertTrue(src.contains("WidgetSample.parse"))
        for (word in listOf("PendingLaunch", "approve", "AppModel", "SecureStore")) assertFalse(word, src.contains(word))
    }

    @Test fun aPreviewWaitsForNothingElseAndLiveGoesBack() {
        val hub = sources.getValue("app/WidgetHub.kt")
        assertTrue(hub.contains("fun preview(context: Context, content: WidgetContent?)"))
        assertTrue(hub.contains("pinned = content"))
        assertTrue(hub.contains("if (pinned != null && content != pinned) return"))
    }

    // ── Every Widget, at its smallest and at a large size ──────────────────────────────────────────

    private fun attr(xml: String, name: String) = Regex("""android:$name="([^"]*)"""").find(xml)?.groupValues?.get(1)
    private fun dp(v: String?) = v!!.removeSuffix("dp").toInt()

    @Test fun everyProviderDeclaresItsSizeAndIsListedWithAStringLabelAndDescription() {
        val strings = read("src/main/res/values/strings.xml")
        val expected = mapOf(
            "kenai_solo_widget_info" to (2 to 2), "kenai_list_widget_info" to (4 to 3), "kenai_big_widget_info" to (4 to 4),
            "kenai_pill_widget_info" to (4 to 1), "kenai_alert_widget_info" to (3 to 2), "kenai_ring_widget_info" to (2 to 2),
            "kenai_today_widget_info" to (2 to 2), "kenai_mood_widget_info" to (1 to 1), "kenai_widget_info" to (4 to 3),
        )
        assertEquals(widgets.map { it.second }.toSet(), expected.keys)
        for ((info, cells) in expected) {
            val xml = read("src/main/res/xml/$info.xml")
            assertEquals(info, cells.first, attr(xml, "targetCellWidth")!!.toInt())
            assertEquals(info, cells.second, attr(xml, "targetCellHeight")!!.toInt())
            // A cell is about 70dp less 30dp of margin: the minimum size is that of its cells.
            assertEquals(info, cells.first * 70 - 30, dp(attr(xml, "minWidth")))
            val high = dp(attr(xml, "minHeight"))
            // «Sesiones y actividad» and the retired List it replaces are taller than their three cells so that their rows fit; the rest
            // are exactly their cells.
            if (info == "kenai_widget_info" || info.contains("list")) assertTrue("$info: ${high}dp", high >= cells.second * 70 - 30)
            else assertEquals(info, cells.second * 70 - 30, high)
            val desc = attr(xml, "description")!!.removePrefix("@string/")
            assertTrue("$info: $desc is not a string", strings.contains("""name="$desc""""))
            assertTrue(xml.contains("""android:initialLayout="@layout/widget_"""))
            // Never smaller than it can be drawn: a resize may not go below the minimum.
            attr(xml, "minResizeWidth")?.let { assertTrue(info, dp(it) <= dp(attr(xml, "minWidth"))) }
        }
        for ((cls, _) in widgets) {
            val label = Regex("""android:name="\.app\.$cls"[^>]*android:label="@string/(\w+)"""").find(manifest)!!.groupValues[1]
            assertTrue("$cls label $label", strings.contains("""name="$label""""))
        }
    }

    // ── «Aprobaciones pendientes» (kenai-0.1 S9, screen 5) ───────────────────────────────────────

    // ── The pending section of «Sesiones y actividad» (the merged «Aprobaciones pendientes») ──────

    @Test fun thePendingSectionStillFitsWhereTheAlertWasPlacedAndKeepsItsRows() {
        val info = read("src/main/res/xml/kenai_alert_widget_info.xml")
        // The retired shell keeps the size it was placed at (3x2, never under 2x2) and is hidden from the picker (L6).
        assertEquals("110", attr(info, "minResizeWidth")!!.removeSuffix("dp"))
        assertEquals("110", attr(info, "minResizeHeight")!!.removeSuffix("dp"))
        assertEquals("horizontal|vertical", attr(info, "resizeMode"))
        assertTrue("hidden from the picker on Android 12 and later", info.contains("""android:widgetFeatures="hide_from_picker""""))
        // Its picture is the new card, so a placed instance loses nothing.
        assertTrue(info.contains("""android:initialLayout="@layout/widget_glance""""))
        val src = read(main + "app/GlanceWidget.kt")
        // The rows are the phone's: one per request or question, in its agent's own colour, with the short word.
        assertTrue(src.contains("plan.pending"))
        assertTrue(src.contains("""setColorFilter", row.argb"""))
        assertTrue(src.contains("row.name") && src.contains("row.word"))
        // The count is the phone's too (alertCount), not the questions inferred from the agents' states.
        assertTrue(read(main + "core/ActivityPlan.kt").contains("content.alertCount"))
    }

    @Test fun thePendingButtonOpensHomeAndSaysWhatWaits() {
        val src = read(main + "app/GlanceWidget.kt")
        // A tap anywhere opens the app; «Revisar el pedido» («Responder la pregunta» when only questions wait) opens Home, whose hero is the
        // first request.
        assertTrue(src.contains("v.setOnClickPendingIntent(R.id.widget_root, WidgetHub.openApp(context, WidgetHub.CODE_GLANCE))"))
        assertTrue(src.contains("v.setOnClickPendingIntent(R.id.glance_review, WidgetHub.openScreen(context, WidgetHub.CODE_GLANCE_BUTTON, LaunchScreen.HOME))"))
        assertTrue(src.contains("setContentDescription(R.id.glance_review"))
        assertTrue(src.contains("v.setTextViewText(R.id.glance_review, button)"))
        // The button says what waits, in the phone's language, from the one rule: the request first, a question when only questions wait.
        val data = read(main + "core/WidgetData.kt")
        assertTrue(data.contains("fun pendingButton(w: Words)"))
        assertTrue(data.contains("w.replyToTheQuestion else w.reviewTheRequest"))
    }

    @Test fun theAlertToneAndItsCountSurviveTheMergeIntoOneCard() {
        // Merging two Widgets into one card means one tone for the whole card (the look of the content, which is ATTENTION while anything
        // waits). What must not degrade: the count and the rows are the phone's own, each row keeps its agent's colour, and a calm card
        // when nothing waits.
        val plan = read(main + "core/ActivityPlan.kt")
        assertTrue("the card's paint is the content's look", plan.contains("tone = content.look"))
        assertTrue("the badge is what the phone holds", plan.contains("badge = content.alertCount.takeIf { waiting }"))
        assertTrue("nothing waits: no pending rows at all", plan.contains("pending = if (waiting) content.alertRows.take(pendingRows(limit)) else emptyList()"))
        val src = read(main + "app/GlanceWidget.kt")
        assertTrue(src.contains("WidgetHub.paint(v, R.id.widget_root, R.id.glance_tint, plan.tone)"))
        // The old alert's own tone and mood are gone with it: nothing reads them any more.
        val data = read(main + "core/WidgetData.kt")
        for (dead in listOf("c.alertLook", "c.alertMood")) assertFalse(dead, src.contains(dead))
        assertTrue("the alert's words are still the ones used", data.contains("val alertRows: List<AlertRow>"))
    }

    @Test fun theListNeverGoesBelowTwoRowsAndTheBigOneKeepsItsBubble() {
        val list = read("src/main/res/xml/kenai_list_widget_info.xml")
        // Header 28 + padding 20 + two rows of 48 = 144dp.
        assertTrue(dp(attr(list, "minResizeHeight")) >= 144)
        assertTrue(dp(attr(list, "minHeight")) >= 28 + 20 + 3 * 48)
        val big = read("src/main/res/xml/kenai_big_widget_info.xml")
        assertTrue(dp(attr(big, "minResizeHeight")) >= 180)
        val pill = read("src/main/res/xml/kenai_pill_widget_info.xml")
        assertEquals("horizontal", attr(pill, "resizeMode"))
        assertEquals("none", attr(read("src/main/res/xml/kenai_mood_widget_info.xml"), "resizeMode"))
    }

    @Test fun everyWidgetIsOnTheSharedCardAndOnlyUsesWhatRemoteViewsAllow() {
        val allowed = setOf("FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout", "TextView", "ImageView", "ProgressBar", "Button")
        for (layout in File("src/main/res/layout").listFiles()!!.filter { it.name.startsWith("widget_") }) {
            val xml = layout.readText()
            assertTrue(layout.name, xml.contains("@drawable/widget_card"))
            // A real layout starts muted and is repainted by WidgetHub; a preview is already in the tone of its sample.
            if (layout.name.startsWith("widget_preview_")) assertTrue(layout.name, xml.contains("@drawable/widget_tint_"))
            else assertTrue(layout.name, xml.contains("@drawable/widget_tint_muted"))
            for (tag in Regex("""<(\w+)[\s>]""").findAll(xml).map { it.groupValues[1] }) assertTrue("${layout.name}: <$tag> in a RemoteViews layout", tag in allowed)
            // Colours come from the shared resources (so the light variant follows the system), not from a literal.
            assertFalse(layout.name, Regex("""android:textColor="#(?!FF1C1C1E)""").containsMatchIn(xml))
        }
    }

    @Test fun theNamesOfTheWidgetsAreWidgetsEverywhere() {
        val strings = File("../i18n/app-strings.json").readText()
        val texts = listOf(strings, File("../README.md").readText()).joinToString("\n")
        assertFalse(texts.contains("Wedge", ignoreCase = true))
        // The picker names are short (Kenai's README does not list them yet).
        for (w in listOf("At a glance", "All your agents", "Big status", "One-line status", "Approvals waiting", "Plan usage", "Finished today", "Agents' mood")) {
            assertTrue(w, strings.contains(w))
        }
    }
}
