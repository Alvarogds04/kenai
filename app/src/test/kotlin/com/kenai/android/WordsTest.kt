package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.Glance
import com.kenai.android.core.GlanceBuilder
import com.kenai.android.core.HomeText
import com.kenai.android.core.PlanUsageLook
import com.kenai.android.core.SafeText
import com.kenai.android.core.ToolLabels
import com.kenai.android.core.Words
import com.kenai.android.core.WidgetData
import com.kenai.android.core.WidgetSample
import com.kenai.android.link.BatteryHint
import com.kenai.android.link.DiagResult
import com.kenai.android.link.DiagStep
import com.kenai.android.link.Diagnostics
import com.kenai.android.link.LinkHealth
import com.kenai.android.link.LinkHealthText
import com.kenai.android.link.LinkState
import com.kenai.android.link.LostReason
import com.kenai.android.link.SessionInfo
import java.io.File
import java.time.ZoneOffset
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The texts built in Kotlin (not in resources) follow the phone's language (kenai-0.1 S2.6, L5 option 2a): Spanish with voseo when
 * the phone is in Spanish, English for every other language. The surfaces seen without opening the app (notifications, widgets,
 * the tile, the island) and Home all read them.
 */
class WordsTest {
    private lateinit var saved: Locale

    @Before fun remember() { saved = Locale.getDefault() }

    @After fun restore() = Locale.setDefault(saved)

    private fun s(id: String, state: BotState, status: String = "") = SessionInfo(id, "", state, status, 0, 0, 0L)
    private val names = mapOf("a" to "Claude Code", "b" to "Codex")
    private fun glance(sessions: List<SessionInfo> = emptyList(), approvals: Int = 0, link: LinkState = LinkState.CONNECTED, paired: Boolean = true): Glance =
        GlanceBuilder.build(paired, link, "la oficina", sessions, approvals) { names[it] ?: "Un agente" }

    private fun spanish() = Locale.setDefault(Locale.forLanguageTag("es-AR"))

    // ── Glance: the notification, the summary widget and the tile ─────────────────────────────────

    @Test fun theGlanceSpeaksSpanishWithItsPlurals() {
        spanish()
        assertEquals("Sin emparejar", glance(paired = false).headline)
        assertEquals("Abrí Kenai para conectarte con tu computadora", glance(paired = false).detail)
        assertEquals("Sin conexión", glance(link = LinkState.DISCONNECTED).headline)
        assertEquals("Conectando…", glance(link = LinkState.CONNECTING).headline)
        assertEquals("Te está esperando", glance(listOf(s("a", BotState.APPROVAL)), approvals = 1).headline)
        assertEquals("1 pedido para aprobar", glance(listOf(s("a", BotState.APPROVAL)), approvals = 1).detail)
        assertEquals("3 pedidos para aprobar", glance(listOf(s("a", BotState.APPROVAL)), approvals = 3).detail)
        assertEquals("1 pregunta de un agente", glance(listOf(s("a", BotState.QUESTION))).detail)
        assertEquals("2 preguntas de agentes", glance(listOf(s("a", BotState.QUESTION), s("b", BotState.QUESTION))).detail)
        assertEquals("1 agente se detuvo", glance(listOf(s("a", BotState.ERROR))).detail)
        assertEquals("2 agentes se detuvieron", glance(listOf(s("a", BotState.ERROR), s("b", BotState.RATELIMIT))).detail)
        val one = glance(listOf(s("a", BotState.WORKING)))
        assertEquals("Claude Code está trabajando", one.headline)
        assertEquals("En la oficina", one.detail)
        assertEquals("2 agentes están trabajando", glance(listOf(s("a", BotState.WORKING), s("b", BotState.THINKING))).headline)
        assertEquals("Todo listo", glance(listOf(s("a", BotState.FINISHED))).headline)
        assertEquals("Conectado", glance().headline)
        assertEquals("Con la oficina", glance().detail)
    }

    @Test fun eachAgentsLineIsASpanishStateWord() {
        spanish()
        val words = mapOf(
            BotState.APPROVAL to "Esperando tu aprobación", BotState.QUESTION to "Tiene una pregunta", BotState.ERROR to "Se detuvo por un error",
            BotState.RATELIMIT to "Llegó a un límite", BotState.FINISHED to "Terminó", BotState.IDLE to "En reposo", BotState.THINKING to "Pensando",
            BotState.SEARCHING to "Buscando", BotState.WORKING to "Trabajando",
        )
        for ((state, word) in words) assertEquals("$state", word, GlanceBuilder.lineOf(s("a", state)))
    }

    // ── The widgets ───────────────────────────────────────────────────────────────────────────────

    @Test fun theWidgetLinesSpeakSpanish() {
        spanish()
        val waiting = WidgetData.build(glance(listOf(s("a", BotState.APPROVAL)), approvals = 2), listOf(s("a", BotState.APPROVAL)), { names[it] ?: "?" })
        assertEquals("2 esperando", waiting.alertLine)
        assertEquals("Claude Code", waiting.alertSub)
        val calm = WidgetData.build(glance(), emptyList(), { "?" })
        assertEquals("Todo tranquilo", calm.alertLine)
        assertEquals("Nada te necesita", calm.alertSub)
        assertEquals("No hay agentes en marcha", calm.emptyLine)
        val away = WidgetData.build(glance(link = LinkState.DISCONNECTED), emptyList(), { "?" })
        assertEquals("Sin conexión", away.alertLine)
        assertEquals("Esperando tu computadora", away.alertSub)
        assertEquals("Esperando tu computadora", away.emptyLine)
        // The made-up previews read like the real thing.
        assertEquals("Te está esperando", WidgetData.sample(WidgetSample.WAITING).headline)
    }

    @Test fun theResetTimeUsesSpanishDayAndMonthNames() {
        spanish()
        val utc = ZoneOffset.UTC
        val now = java.time.LocalDateTime.of(2026, 10, 12, 9, 0).toInstant(utc).toEpochMilli() // a Monday
        fun at(d: Int, h: Int, m: Int) = java.time.LocalDateTime.of(2026, 10, d, h, m).toInstant(utc).toEpochMilli()
        assertEquals("se reinicia a las 14:30", PlanUsageLook.resetLine(at(12, 14, 30), now, utc))
        assertEquals("se reinicia el vie 14:30", PlanUsageLook.resetLine(at(16, 14, 30), now, utc))
        assertEquals("se reinicia el 21 oct 14:30", PlanUsageLook.resetLine(at(21, 14, 30), now, utc))
        assertEquals("Cerca del límite · se reinicia a las 14:30", PlanUsageLook.note(95, at(12, 14, 30), now, utc))
    }

    // ── Home and the island ───────────────────────────────────────────────────────────────────────

    @Test fun homeTalksAboutDecisionsInSpanish() {
        spanish()
        // Said to you, as the artboard (kenai-0.1 S9): «Permitiste: …», «Denegaste: …».
        assertEquals("Permitiste: editar archivos", HomeText.recent(true, "Edit"))
        assertEquals("Denegaste: ejecutar un comando", HomeText.recent(false, "Bash"))
        assertEquals("Le permitiste a Claude Code ejecutar un comando", HomeText.decision(true, "Claude Code", "Bash"))
        assertEquals("Le denegaste a un agente leer archivos", HomeText.decision(false, "", "Read"))
    }

    @Test fun toolsAreNamedInSpanish() {
        spanish()
        assertEquals("Ejecutando un comando · npm", ToolLabels.label("Bash · npm test"))
        assertEquals("Editando archivos", ToolLabels.label("Edit"))
        assertEquals("Leyendo archivos", ToolLabels.label("read_file"))
        assertEquals("Buscando en la web", ToolLabels.label("WebSearch"))
        assertEquals("Haciendo una pregunta", ToolLabels.label("ask_question"))
        assertEquals("Planificando", ToolLabels.label("TodoWrite"))
        assertEquals("hacer algo", ToolLabels.action(""))
        assertEquals("leer una página web", ToolLabels.action("WebFetch"))
        // An unknown tool is only cleaned, in any language.
        assertEquals("Mcp github list issues", ToolLabels.label("mcp__github__list_issues"))
        assertEquals("Trabajando", SafeText.requestLabel("", null))
        assertEquals("Ejecutando un comando · npm", SafeText.requestLabel("Bash", "npm test"))
    }

    // ── The link ──────────────────────────────────────────────────────────────────────────────────

    @Test fun aLostLinkIsToldInSpanish() {
        spanish()
        val h = LinkHealth().lost(1_000_000 - 3 * 60_000, LostReason.SILENT, screenWasOff = true)
        val (head, why) = LinkHealthText.lines(false, h, 1_000_000)!!
        assertEquals("Conexión perdida hace 3 min, reconectando", head)
        assertTrue(why, why.startsWith("La computadora dejó de responder"))
        assertEquals("menos de un minuto", LinkHealthText.ago(0))
        assertTrue(LinkHealthText.lines(false, h.failedAttempt(4), 1_000_000)!!.first.endsWith("(4 intentos)"))
        assertTrue(BatteryHint.steps(samsung = false).single().startsWith("Abrí"))
        assertEquals(2, BatteryHint.steps(samsung = true).size)
    }

    @Test fun theChecksAndTheCopiedReportAreNamedInSpanish() {
        spanish()
        assertEquals("Red del teléfono", DiagStep.NETWORK.title)
        assertEquals("Código de emparejamiento y versión", DiagStep.TOKEN.title)
        val down = Diagnostics.run(object : com.kenai.android.link.DiagEnv {
            override fun network() = DiagResult.Problem("x", "y")
            override fun address() = DiagResult.Ok("ok")
            override fun discovery() = DiagResult.Ok("ok")
            override fun secure() = DiagResult.Ok("ok")
            override fun token() = DiagResult.Ok("ok")
        })
        assertEquals(DiagResult.Skipped("Necesita el Wi-Fi"), down[1].second)
        // «todo en español» (S6): the copied report follows the phone too, frame, check names and the hidden name.
        val report = Diagnostics.report("0.1", "14", "Pixel", down, Diagnostics.maskHost("pc.local"), "sin conexión")
        assertTrue(report, report.startsWith("Kenai para Android 0.1 · Android 14 · Pixel"))
        assertTrue(report, report.contains("PROBLEMA    Red del teléfono: x"))
        assertTrue(report, report.contains("SIN PROBAR  La dirección guardada responde: Necesita el Wi-Fi"))
        assertTrue(report, report.contains("Computadora: un nombre (oculto) · conectada por: sin conexión"))
        Locale.setDefault(Locale.ENGLISH)
        val english = Diagnostics.report("0.1", "14", "Pixel", down, Diagnostics.maskHost("pc.local"), "not connected")
        assertTrue(english, english.contains("PROBLEM  Phone network: x"))
        assertTrue(english, english.contains("Computer: a name (hidden) · connected via: not connected"))
    }

    /** The app's "connected via" and "no computer" words in the copied report come from the words, not from English literals. */
    @Test fun theReportsConnectionWordsFollowThePhone() {
        val src = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        val body = src.substringAfter("fun diagnosticsReport(").substringBefore("\n    }\n")
        for (literal in listOf("\"not connected\"", "\"the direct link\"", "\"none\"")) assertFalse("diagnosticsReport says $literal", body.contains(literal))
        assertTrue(body, body.contains("Words.current()"))
    }

    // ── The notification channels ─────────────────────────────────────────────────────────────────

    @Test fun theNotificationChannelsAreNamedInThePhonesLanguage() {
        assertEquals("Conexión", Words.ES.channelLink)
        assertEquals("Aprobaciones", Words.ES.channelApprovals)
        assertEquals("Aprobaciones (silenciosas)", Words.ES.channelApprovalsQuiet)
        assertEquals("Novedades", Words.ES.channelUpdates)
        assertEquals("Approvals", Words.EN.channelApprovals)
        // Every channel takes its name and its description from the words, none from a literal.
        val n = File("src/main/kotlin/com/kenai/android/app/Notifications.kt").readText()
        val channels = Regex("""NotificationChannel\(CH_\w+, ([^,]+),""").findAll(n).map { it.groupValues[1] }.toList()
        assertEquals(listOf("w.channelLink", "w.channelUpdates", "w.channelApprovalsQuiet", "w.channelApprovals"), channels)
        assertEquals(4, Regex("""description = w\.channel\w+About""").findAll(n).count())
        assertTrue(n.contains("val w = Words.current()"))
    }

    // ── Which table ───────────────────────────────────────────────────────────────────────────────

    @Test fun spanishForAnySpanishLocaleEnglishForEveryOtherOne() {
        for (tag in listOf("es", "es-AR", "es-ES", "es-MX", "es-419")) assertTrue(tag, Words.current(Locale.forLanguageTag(tag)) === Words.ES)
        for (tag in listOf("en", "en-US", "fr", "pt-BR", "de", "it", "ja", "ca", "gl")) assertTrue(tag, Words.current(Locale.forLanguageTag(tag)) === Words.EN)
        spanish()
        assertTrue(Words.current() === Words.ES)
        Locale.setDefault(Locale.US)
        assertTrue(Words.current() === Words.EN)
    }

    /** The Spanish matches the resources (res/values-es): «emparejar», «computadora», «pedido», «Ajustes», never «vincular», «PC» or «ordenador». */
    @Test fun theSpanishUsesTheWordsOfTheResources() {
        val phrases = ArrayList<String>()
        for (m in Words::class.java.methods.filter { it.declaringClass == Words::class.java && !java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
            if (m.name == "getLocale") continue
            for (args in argumentsFor(m)) when (val v = m.invoke(Words.ES, *args)) {
                is String -> phrases += v
                is List<*> -> phrases += v.filterIsInstance<String>()
            }
        }
        for (p in phrases) {
            for (banned in listOf("vincul", "Vincul", "ordenador", "PC", "Phone link")) assertTrue("«$p» says $banned", !p.contains(banned))
        }
        assertEquals("Esperando tu computadora", Words.ES.waitingForPc)
        assertEquals("Tu computadora", Words.ES.yourPc)
        assertEquals("Código de emparejamiento aceptado, versión del protocolo 1", Words.ES.codeAccepted(1))
        assertTrue(Words.ES.codeRefusedHint.contains("Volver a emparejar"))
        assertTrue(Words.ES.batterySteps(samsung = true).first().startsWith("Abrí Ajustes"))
        assertEquals("1 pedido para aprobar", Words.ES.requestsToApprove(1))
    }

    /** Every phrase of the Spanish table is filled in, and none is the English one left untranslated. */
    @Test fun noSpanishEntryIsBlankOrLeftInEnglish() {
        val same = ArrayList<String>()
        for (m in Words::class.java.methods.filter { it.declaringClass == Words::class.java && !java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
            if (m.name == "getLocale") continue
            for (args in argumentsFor(m)) {
                val es = m.invoke(Words.ES, *args)
                val en = m.invoke(Words.EN, *args)
                val label = "${m.name}(${args.joinToString()})"
                when (es) {
                    is String -> {
                        assertTrue("$label is blank", es.isNotBlank())
                        if (es == en) same += label
                    }
                    is List<*> -> {
                        assertEquals(label, (en as List<*>).size, es.size)
                        assertTrue("$label has a blank line", es.all { it is String && it.isNotBlank() })
                        if (es == en) same += label
                    }
                    null -> assertEquals("$label: null only where English has none", null, en)
                    else -> error("$label returns ${es.javaClass}")
                }
            }
        }
        // Nothing in the Spanish table is the English phrase left as it was.
        assertEquals(emptyList<String>(), same)
    }

    private fun argumentsFor(m: java.lang.reflect.Method): List<Array<Any>> {
        var out = listOf(emptyArray<Any>())
        for (type in m.parameterTypes) {
            val choices: List<Any> = when {
                type == Int::class.javaPrimitiveType -> listOf(1, 2, 7)
                type == Long::class.javaPrimitiveType -> listOf(12L)
                type == Boolean::class.javaPrimitiveType -> listOf(true, false)
                type == String::class.java -> listOf("Claude Code", "")
                type.isEnum -> type.enumConstants.toList()
                else -> error("no sample for ${type.name} in ${m.name}")
            }
            out = out.flatMap { prefix -> choices.map { prefix + it } }
        }
        return out
    }

    /** Inicio's new words (kenai-0.1 S9, T13): the strip, the request, the calm card and the bar's badge, with voseo. */
    @Test fun inicioSpeaksSpanishWithVoseo() {
        val w = Words.ES
        assertEquals("Claude Code espera tu sí", w.waitsForYourYes("Claude Code"))
        assertEquals("Alvaro PC · 3 agentes", w.computerAgents("Alvaro PC", 3))
        assertEquals("Alvaro PC · 1 agente", w.computerAgents("Alvaro PC", 1))
        assertEquals("Alvaro PC · todo tranquilo", w.computerCalm("Alvaro PC"))
        assertEquals("Sin conexión con Alvaro PC", w.offlineFrom("Alvaro PC"))
        assertEquals("Último dato hace 3 min", w.lastHeard("3 min"))
        assertEquals("Lo último que sabemos · hace 3 min", w.lastKnown("3 min"))
        assertEquals("2 en curso", w.runningCount(2))
        assertEquals("2 te esperan", w.waitingForYouCount(2))
        assertEquals("1 te espera", w.waitingForYouCount(1))
        assertEquals("Caduca en 1:59", w.expiresIn("1:59"))
        assertEquals("Quiere ejecutar un comando", w.wants("ejecutar un comando"))
        assertEquals("Quiere ejecutar un comando en kenai", w.wantsIn("ejecutar un comando", "kenai"))
        assertEquals("dentro de kenai.", w.riskWhere("kenai"))
        assertEquals("Gemini CLI tiene una pregunta", w.asksYou("Gemini CLI"))
    }

    /** The result of a decision (kenai-0.1 S9, screen 3; T14), with voseo. */
    @Test fun theResultSpeaksSpanishWithVoseo() {
        val w = Words.ES
        assertEquals("Enviando a Alvaro PC…", w.sendingTo("Alvaro PC"))
        assertEquals("Permitido · Alvaro PC lo recibió", w.received(true, "Alvaro PC"))
        assertEquals("Denegado · Alvaro PC lo recibió", w.received(false, "Alvaro PC"))
        assertEquals("Ese pedido ya no está pendiente", w.notPendingAnyMore)
        assertEquals("No se pudo enviar", w.couldNotSend)
        assertEquals("Enviando tu decisión", w.sendingTitle)
        assertEquals("Kenai espera que tu computadora confirme que la recibió. No cierres nada.", w.sendingBody)
        assertEquals("Permitiste ejecutar un comando", w.youDecided(true, "ejecutar un comando"))
        assertEquals("Denegaste editar archivos", w.youDecided(false, "editar archivos"))
        assertEquals("Claude Code sigue trabajando en kenai.", w.keepsWorking("Claude Code", "kenai"))
        assertEquals("Claude Code sigue trabajando.", w.keepsWorking("Claude Code", ""))
        assertEquals("Claude Code no lo va a ejecutar. Si querés, explicale por qué en tu computadora.", w.willNotDo("Claude Code", command = true))
        assertEquals("Caducó a las 14:45 o se respondió en tu computadora. Desde el teléfono no se envió nada.", w.expiredBody("14:45"))
        assertEquals("Caducó a las 14:45 sin que tu computadora confirmara tu decisión.", w.expiredUnconfirmed("14:45"))
        assertEquals("Tu decisión no llegó", w.didNotArrive)
        assertEquals("Se perdió la conexión con tu computadora. El pedido sigue abierto: volver a intentar es seguro.", w.failedBody)
        assertEquals("14:41 · vibró una vez · quedó en Actividad", w.resultMeta("14:41", 1))
        assertEquals("14:41 · vibró dos veces · quedó en Actividad", w.resultMeta("14:41", 2))
        assertEquals("14:41 · quedó en Actividad", w.resultMeta("14:41", 0))
        assertEquals("Sending to Alvaro PC…", Words.EN.sendingTo("Alvaro PC"))
        assertEquals("Allowed · Alvaro PC got it", Words.EN.received(true, "Alvaro PC"))
    }

    // ── Any other language: English ───────────────────────────────────────────────────────────────

    @Test fun anyOtherLanguageGetsEnglish() {
        Locale.setDefault(Locale.FRENCH)
        assertEquals("Waiting for you", glance(listOf(s("a", BotState.APPROVAL)), approvals = 2).headline)
        assertEquals("2 requests to approve", glance(listOf(s("a", BotState.APPROVAL)), approvals = 2).detail)
        assertEquals("Running a command", ToolLabels.label("Bash"))
        assertEquals("Connection lost 1 min ago, reconnecting", LinkHealthText.lines(false, LinkHealth().lost(0, LostReason.NETWORK, false), 60_000)!!.first)
        assertEquals("Phone network", DiagStep.NETWORK.title)
        assertEquals("resets at 14:30", PlanUsageLook.resetLine(52_200_000L, 0L, ZoneOffset.UTC))
    }
}
