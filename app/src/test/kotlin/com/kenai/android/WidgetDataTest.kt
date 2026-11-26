package com.kenai.android

import com.kenai.android.core.Glance
import com.kenai.android.core.GlanceBuilder
import com.kenai.android.core.GlanceTone
import com.kenai.android.core.WidgetData
import com.kenai.android.link.LinkState
import com.kenai.android.link.PlanUsage
import com.kenai.android.link.PlanWindow
import com.kenai.android.link.SessionInfo
import com.kenai.android.link.UsageSnapshot
import com.kenai.android.core.PlanUsageLook
import com.kenai.android.core.UsageRing
import com.kenai.android.core.WidgetTone
import com.kenai.android.character.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {
    private fun s(id: String, state: BotState, color: String? = null, status: String = "") =
        SessionInfo(id, "", state, status, 0, 0, 0L, project = "secret-folder", color = color)

    private val names = mapOf("agent_codex" to "Codex", "agent_gemini" to "Gemini CLI", "agent_amp" to "Amp")
    private fun glance(sessions: List<SessionInfo>, approvals: Int = 0, link: LinkState = LinkState.CONNECTED): Glance =
        GlanceBuilder.build(true, link, "PC", sessions, approvals) { names[it] ?: "An agent" }

    private fun build(sessions: List<SessionInfo>, approvals: Int = 0, link: LinkState = LinkState.CONNECTED) =
        WidgetData.build(glance(sessions, approvals, link), sessions, nameOf = { names[it] ?: "An agent" })

    /** The alert as the app builds it: the phone's own requests and questions (pillIds), the glance counting the requests. */
    private fun alert(sessions: List<SessionInfo>, requests: List<String> = emptyList(), questions: List<String> = emptyList(), link: LinkState = LinkState.CONNECTED) =
        WidgetData.build(glance(sessions, requests.size, link), sessions, nameOf = { names[it] ?: "An agent" }, requests = requests, questions = questions)

    @Test fun theMostUrgentAgentComesFirstLikeOnTheOtherSurfaces() {
        val sessions = listOf(s("agent_amp", BotState.IDLE), s("agent_codex", BotState.WORKING), s("agent_gemini", BotState.APPROVAL))
        val c = build(sessions, approvals = 1)
        assertEquals(listOf("agent_gemini", "agent_codex", "agent_amp"), c.tiles.map { it.pillId })
        assertEquals("agent_gemini", c.top!!.pillId)
        // The same order as the notification and the first widget.
        assertEquals(c.tiles.map { it.name }, glance(sessions, 1).lines.map { it.agent })
    }

    @Test fun theStateWordIsTheOneOfTheGlance() {
        val sessions = listOf(s("agent_codex", BotState.QUESTION))
        assertEquals("Has a question", build(sessions).top!!.stateWord)
        assertEquals("Codex", build(sessions).top!!.name)
        assertEquals(glance(sessions).lines.single().text, build(sessions).top!!.stateWord)
    }

    @Test fun theListIsCapped() {
        val many = (1..9).map { s("p$it", BotState.WORKING) }
        assertEquals(WidgetData.MAX_ROWS, build(many).tiles.size)
    }

    @Test fun theColourIsTheSessionsOwnThenThePillsThenGrey() {
        assertEquals(0xFF112233.toInt(), build(listOf(s("agent_codex", BotState.IDLE, color = "#112233"))).top!!.argb)
        assertEquals(0xFF2DD4BF.toInt(), build(listOf(s("agent_codex", BotState.IDLE))).top!!.argb)
        assertEquals(0xFF2DD4BF.toInt(), build(listOf(s("agent_codex", BotState.IDLE, color = "teal"))).top!!.argb)
        assertEquals(GlanceTone.CALM.argb, build(listOf(s("nope", BotState.IDLE))).top!!.argb)
    }

    @Test fun parseOnlyTakesSixDigitHex() {
        assertEquals(0xFFFFFFFF.toInt(), WidgetData.parse("#FFFFFF"))
        for (bad in listOf(null, "", "FFFFFF", "#FFF", "#GGGGGG", "#FFFFFFFF", "#+12345")) assertNull(bad, WidgetData.parse(bad))
    }

    @Test fun nothingToShowWhileNotConnected() {
        val c = build(listOf(s("agent_codex", BotState.WORKING)), link = LinkState.DISCONNECTED)
        assertTrue(c.tiles.isEmpty())
        assertNull(c.top)
        assertFalse(c.connected)
        assertEquals(BotState.SLEEPING, c.mood)
        assertEquals("Not connected", c.headline)
    }

    @Test fun connectedWithNoAgentIsCalm() {
        val c = build(emptyList())
        assertEquals(BotState.IDLE, c.mood)
        assertEquals("Connected", c.headline)
    }

    @Test fun moodIsTheTopAgents() {
        assertEquals(BotState.ERROR, build(listOf(s("agent_codex", BotState.WORKING), s("agent_amp", BotState.ERROR))).mood)
    }

    @Test fun waitingCountsRequestsAndQuestions() {
        val c = build(listOf(s("agent_codex", BotState.QUESTION)), approvals = 2)
        assertEquals(3, c.waiting)
    }

    @Test fun sameContentIsEqualSoThePushCanBeSkipped() {
        val a = build(listOf(s("agent_codex", BotState.WORKING)))
        val b = build(listOf(s("agent_codex", BotState.WORKING)))
        assertEquals(a, b)
        // A change the widget does not show (step text, project) does not change the content.
        assertEquals(a, build(listOf(s("agent_codex", BotState.WORKING, status = "editing a.kt"))))
        assertFalse(a == build(listOf(s("agent_codex", BotState.THINKING))))
    }

    @Test fun nothingPrivateReachesTheTiles() {
        val c = build(listOf(s("agent_codex", BotState.WORKING, status = "rm -rf /home/me/secret.txt")), approvals = 1)
        val text = c.toString()
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("rm -rf"))
    }

    @Test fun anEmptyCardSaysOneClearLine() {
        assertEquals("Waiting for your computer", build(emptyList(), link = LinkState.DISCONNECTED).emptyLine)
        assertEquals("Waiting for your computer", build(listOf(s("agent_codex", BotState.WORKING)), link = LinkState.CONNECTING).emptyLine)
        assertEquals("No agents running", build(emptyList()).emptyLine)
        assertNull(build(listOf(s("agent_codex", BotState.WORKING))).emptyLine)
    }

    @Test fun aTilesDotIsTheToneOfItsState() {
        val t = build(listOf(s("agent_codex", BotState.APPROVAL), s("agent_amp", BotState.FINISHED))).tiles
        assertEquals(com.kenai.android.core.WidgetTone.ATTENTION, t[0].tone)
        assertEquals(com.kenai.android.core.WidgetTone.DONE, t[1].tone)
    }

    // ── The alert, the ring and the day's count ──────────────────────────────────

    @Test fun theAlertIsCalmUntilSomethingWaits() {
        assertEquals("All calm", build(emptyList()).alertLine)
        assertEquals("1 waits for you", alert(listOf(s("agent_codex", BotState.APPROVAL)), requests = listOf("agent_codex")).alertLine)
        assertEquals("3 wait for you", alert(listOf(s("agent_codex", BotState.QUESTION)), requests = listOf("agent_amp", "agent_gemini")).alertLine)
        assertEquals("Not connected", build(emptyList(), link = LinkState.DISCONNECTED).alertLine)
        assertEquals(WidgetTone.ATTENTION, build(listOf(s("agent_codex", BotState.APPROVAL)), approvals = 1).look)
        assertEquals("connected with nothing to list is calm", WidgetTone.IDLE, build(emptyList()).look)
    }

    // ── The alert's own tone (kenai-0.1 T18): calm unless something waits ─────────────────────────

    @Test fun anAgentInErrorWithNothingWaitingLeavesTheAlertCalm() {
        val c = alert(listOf(s("agent_codex", BotState.ERROR), s("agent_amp", BotState.WORKING)))
        assertEquals(WidgetTone.IDLE, c.alertLook)
        assertEquals(BotState.IDLE, c.alertMood)
        assertEquals("All calm", c.alertLine)
        // The other widgets still show the top agent's trouble.
        assertEquals(WidgetTone.TROUBLE, c.look)
        assertEquals(BotState.ERROR, c.mood)
    }

    @Test fun theAlertTurnsOrangeWhenSomethingWaitsAndMutedWhenAway() {
        val request = alert(listOf(s("agent_codex", BotState.ERROR), s("agent_amp", BotState.WORKING)), requests = listOf("agent_amp"))
        assertEquals(WidgetTone.ATTENTION, request.alertLook)
        assertEquals(BotState.APPROVAL, request.alertMood)
        val question = alert(listOf(s("agent_codex", BotState.ERROR)), questions = listOf("agent_gemini"))
        assertEquals(WidgetTone.ATTENTION, question.alertLook)
        assertEquals("only a question waits", BotState.QUESTION, question.alertMood)
        val both = alert(emptyList(), requests = listOf("agent_amp"), questions = listOf("agent_gemini"))
        assertEquals(BotState.APPROVAL, both.alertMood)
        val away = alert(listOf(s("agent_codex", BotState.ERROR)), requests = listOf("agent_codex"), link = LinkState.DISCONNECTED)
        assertEquals(WidgetTone.MUTED, away.alertLook)
        assertEquals(BotState.SLEEPING, away.alertMood)
        assertEquals(0, away.alertCount)
    }

    @Test fun theAlertButtonSaysWhatWaits() {
        assertEquals("Review request", alert(emptyList(), requests = listOf("agent_amp")).alertButton)
        assertEquals("Review request", alert(emptyList(), requests = listOf("agent_amp"), questions = listOf("agent_gemini")).alertButton)
        assertEquals("Answer the question", alert(emptyList(), questions = listOf("agent_gemini")).alertButton)
    }

    // ── «Aprobaciones pendientes» (kenai-0.1 S9, screen 5) ───────────────────────────────────────

    @Test fun underTheCountTheAlertNamesTheComputer() {
        val sessions = listOf(s("agent_codex", BotState.APPROVAL))
        val named = WidgetData.build(glance(sessions, 1), sessions, nameOf = { names[it] ?: "An agent" }, desktop = "Alvaro PC", requests = listOf("agent_codex"))
        assertEquals("Alvaro PC", named.desktop)
        assertEquals("Alvaro PC", named.alertSub)
        assertEquals("no name yet: the plain words", "Your PC", alert(sessions, requests = listOf("agent_codex")).alertSub)
        assertEquals("Your PC", WidgetData.build(glance(sessions, 1), sessions, nameOf = { "x" }, desktop = "  ", requests = listOf("agent_codex")).alertSub)
        // Calm and away say what they always said.
        assertEquals("Nothing needs you", WidgetData.build(glance(emptyList()), emptyList(), nameOf = { "x" }, desktop = "Alvaro PC").alertSub)
        assertEquals("Waiting for your PC", build(emptyList(), link = LinkState.DISCONNECTED).alertSub)
    }

    @Test fun theAlertRowsAreTheAgentsThatWaitWithTheirOwnColourAndAShortWord() {
        val sessions = listOf(
            s("agent_amp", BotState.WORKING), s("agent_codex", BotState.APPROVAL, color = "#D97757"), s("agent_gemini", BotState.QUESTION, color = "#4285F4"),
        )
        val rows = alert(sessions, requests = listOf("agent_codex"), questions = listOf("agent_gemini")).alertRows
        assertEquals(listOf("Codex", "Gemini CLI"), rows.map { it.name })
        assertEquals(listOf("Waiting for your OK", "Has a question"), rows.map { it.word })
        assertEquals(listOf(0xFFD97757.toInt(), 0xFF4285F4.toInt()), rows.map { it.argb })
        assertEquals(listOf("agent_codex", "agent_gemini"), rows.map { it.pillId })
        // Three at most, and none when nothing waits or the computer is away.
        val many = (0 until 5).map { s("p$it", BotState.APPROVAL) }
        assertEquals(WidgetData.ALERT_ROWS, alert(many, requests = many.map { it.pillId }).alertRows.size)
        assertEquals(3, WidgetData.ALERT_ROWS)
        assertTrue(build(listOf(s("agent_amp", BotState.WORKING))).alertRows.isEmpty())
        assertTrue(alert(sessions, requests = listOf("agent_codex"), link = LinkState.DISCONNECTED).alertRows.isEmpty())
    }

    @Test fun theRowsComeFromThePhonesRequestsAndQuestionsSoTheCountAgrees() {
        // The bug of L27: the desktop shows the agent in QUESTION while the phone holds its request and its question.
        val sessions = listOf(s("agent_codex", BotState.QUESTION), s("agent_amp", BotState.ERROR))
        val c = alert(sessions, requests = listOf("agent_codex"), questions = listOf("agent_codex"))
        assertEquals("one row per waiting thing, the request first", listOf("Codex" to "Waiting for your OK", "Codex" to "Has a question"), c.alertRows.map { it.name to it.word })
        assertEquals(2, c.alertCount)
        assertEquals("2 wait for you", c.alertLine)
        // The count and the rows agree up to three rows; above, the count goes on and the rows stop at three.
        for (n in 0..5) {
            val ids = (0 until n).map { "p$it" }
            val built = alert(emptyList(), requests = ids.take(n / 2), questions = ids.drop(n / 2))
            assertEquals("$n waiting", n, built.alertCount)
            assertEquals("$n waiting", minOf(n, WidgetData.ALERT_ROWS), built.alertRows.size)
        }
        // Requests before questions, whatever the desktop's states say.
        val order = alert(listOf(s("agent_gemini", BotState.APPROVAL), s("agent_codex", BotState.WORKING)), requests = listOf("agent_codex", "agent_amp"), questions = listOf("agent_gemini"))
        assertEquals(listOf("agent_codex", "agent_amp", "agent_gemini"), order.alertRows.map { it.pillId })
    }

    @Test fun aRowWithNoTileTakesThePillsNameAndColourElseItsId() {
        // Not among the sessions: the catalogue's name and colour.
        val known = alert(emptyList(), requests = listOf("agent_opencode")).alertRows.single()
        assertEquals("OpenCode", known.name)
        assertEquals(0xFF4ADE80.toInt(), known.argb)
        // Not in the catalogue either: its id and the calm grey.
        val unknown = alert(emptyList(), questions = listOf("agent_nobody")).alertRows.single()
        assertEquals("agent_nobody", unknown.name)
        assertEquals(GlanceTone.CALM.argb, unknown.argb)
    }

    @Test fun aQuestionTheDesktopShowsCountsEvenWhenThePhoneHasNoCardForIt() {
        // Without the answers capability the phone holds no question, but the agent still waits: one row, as the glance counts it.
        val c = alert(listOf(s("agent_gemini", BotState.QUESTION)))
        assertEquals(listOf("agent_gemini"), c.waitingQuestions)
        assertEquals(c.waiting, c.alertCount)
        assertEquals(listOf("Gemini CLI" to "Has a question"), c.alertRows.map { it.name to it.word })
        // The same agent's question held by the phone is not counted twice.
        assertEquals(1, alert(listOf(s("agent_gemini", BotState.QUESTION)), questions = listOf("agent_gemini")).alertCount)
    }

    private val now = 1_000_000L
    private fun usage(claude5: Int? = null, claudeWeek: Int? = null, codex5: Int? = null, resets: Long = now + 3_600_000) = UsageSnapshot(
        if (claude5 != null || claudeWeek != null) PlanUsage(claude5?.let { PlanWindow(it, resets) }, claudeWeek?.let { PlanWindow(it, resets) }) else null,
        codex5?.let { PlanUsage(PlanWindow(it, resets), null) },
    )

    private fun built(u: UsageSnapshot?, link: LinkState = LinkState.CONNECTED) =
        WidgetData.build(glance(emptyList(), 0, link), emptyList(), { "x" }, u, today = 0, nowMs = now)

    @Test fun theRingPrefersClaudesFiveHoursThenItsWeekThenCodex() {
        assertEquals(UsageRing("Claude", "5 h", 42), built(usage(claude5 = 42, claudeWeek = 10, codex5 = 90)).ring)
        assertEquals(UsageRing("Claude", "week", 10), built(usage(claudeWeek = 10, codex5 = 90)).ring)
        assertEquals(UsageRing("Codex", "5 h", 90, "Near limit · " + PlanUsageLook.resetLine(now + 3_600_000L, now)), built(usage(codex5 = 90)).ring)
        assertNull(built(usage()).ring)
        assertNull("the switch is off", built(null).ring)
    }

    @Test fun aWindowThatHasResetCountsAsZeroAndNothingShowsWhenNotConnected() {
        assertEquals(0, built(usage(claude5 = 80, resets = now - 1)).ring!!.pct)
        assertNull(built(usage(claude5 = 42), LinkState.DISCONNECTED).ring)
    }

    @Test fun theRingIsTealThenYellowThenRedInItsOwnPalette() {
        assertEquals(com.kenai.android.core.UsageTone.CALM, UsageRing("Claude", "5 h", 49).tone)
        assertEquals(com.kenai.android.core.UsageTone.CALM, UsageRing("Claude", "5 h", 74).tone)
        assertEquals(com.kenai.android.core.UsageTone.CLOSE, UsageRing("Claude", "5 h", 75).tone)
        assertEquals(com.kenai.android.core.UsageTone.LIMIT, UsageRing("Claude", "5 h", 90).tone)
        assertEquals(0.42f, UsageRing("Claude", "5 h", 42).fraction, 0.0001f)
        assertEquals(1f, UsageRing("Claude", "5 h", 140).fraction, 0f)
        assertEquals(100, UsageRing("Claude", "5 h", 140).shown)
        assertEquals(0, UsageRing("Claude", "5 h", -3).shown)
        assertEquals(0f, UsageRing("Claude", "5 h", -3).fraction, 0f)
        assertEquals("Claude · 5 h", UsageRing("Claude", "5 h", 1).label)
    }

    @Test fun todayRidesAlongAndIsNeverNegative() {
        val g = glance(emptyList())
        assertEquals(4, WidgetData.build(g, emptyList(), { "x" }, today = 4).today)
        assertEquals(0, WidgetData.build(g, emptyList(), { "x" }, today = -2).today)
        assertFalse(WidgetData.build(g, emptyList(), { "x" }, today = 4) == WidgetData.build(g, emptyList(), { "x" }, today = 5))
    }

    // KN-13 T2: «Sesiones y actividad» data, real engine values only (S2, S4).
    private fun stepped(id: String, state: BotState, index: Int, count: Int) =
        SessionInfo(id, "", state, "editing a.kt", index, count, 0L)

    @Test fun aTileCarriesTheEnginesStepsAsNOfM() {
        val c = build(listOf(stepped("agent_codex", BotState.WORKING, 3, 8)))
        assertEquals("3/8", c.tiles.single().progress)
    }

    @Test fun noStepsWhenTheEngineSentNone() {
        assertNull(build(listOf(stepped("agent_codex", BotState.WORKING, 0, 0))).tiles.single().progress)
        assertNull(build(listOf(stepped("agent_codex", BotState.WORKING, 2, -1))).tiles.single().progress)
    }

    @Test fun stepsAreNeverMadeIntoAPercentageOrPastTheTotal() {
        assertEquals("6/6", build(listOf(stepped("agent_codex", BotState.WORKING, 9, 6))).tiles.single().progress)
        assertEquals("0/6", build(listOf(stepped("agent_codex", BotState.WORKING, -2, 6))).tiles.single().progress)
        assertFalse(build(listOf(stepped("agent_codex", BotState.WORKING, 3, 6))).toString().contains("%"))
    }

    @Test fun theStepTextStillNeverReachesTheTiles() {
        val c = build(listOf(stepped("agent_codex", BotState.WORKING, 3, 8)))
        assertFalse(c.toString().contains("editing a.kt"))
    }

    @Test fun theFinishedTodayCountIsLabelledApproximateAndPhoneOnly() {
        val g = glance(emptyList())
        val none = WidgetData.build(g, emptyList(), nameOf = { it }, today = 0)
        val three = WidgetData.build(g, emptyList(), nameOf = { it }, today = 3)
        assertNull(none.todayApproxLine(com.kenai.android.core.Words.EN))
        assertEquals("3 finished today · approx., this phone", three.todayApproxLine(com.kenai.android.core.Words.EN))
        assertEquals("the same line in the phone's own language", three.todayApprox, three.todayApproxLine(com.kenai.android.core.Words.current()))
    }

    @Test fun theFinishedTodayLineIsPinnedInSpanishThroughWords() {
        // The line is the only number this phone counts, so it must say so in Spanish too (KN-13 T2 review, advisory 4).
        val g = glance(emptyList())
        val es = WidgetData.build(g, emptyList(), nameOf = { it }, today = 3).todayApproxLine(com.kenai.android.core.Words.ES)
        assertEquals("3 terminadas hoy · aprox., este teléfono", es)
        assertTrue(es!!.contains("aprox.") && es.contains("este teléfono"))
        assertNull(WidgetData.build(g, emptyList(), nameOf = { it }, today = 0).todayApproxLine(com.kenai.android.core.Words.ES))
    }

    @Test fun theActivityWordsExistInSpanishToo() {
        assertEquals("Sessions & activity", com.kenai.android.core.ActivityWords.of(com.kenai.android.core.Words.EN).title)
        assertEquals("Sesiones y actividad", com.kenai.android.core.ActivityWords.of(com.kenai.android.core.Words.ES).title)
        assertEquals("3 finished today · approx., this phone", com.kenai.android.core.ActivityWords.of(com.kenai.android.core.Words.EN).finishedTodayApprox(3))
        assertEquals("3 terminadas hoy · aprox., este teléfono", com.kenai.android.core.ActivityWords.of(com.kenai.android.core.Words.ES).finishedTodayApprox(3))
    }
}
