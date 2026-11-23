package com.kenai.android

import com.kenai.android.core.Refusal
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guards for a refused answer (KN-04 T3, S4/S12). AppModel cannot be built in a JVM test (it needs a Context) and
 * Compose cannot be rendered, so what is checked here is that Kenai — not the engine's own sentence, and never «Link error» —
 * speaks about each refusal, and that a card that was only sent is not dropped by the phone alone.
 */
class RefusalHandlingTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val appModel get() = src("app/AppModel.kt")
    private val linkClient get() = src("link/LinkClient.kt")
    private val strings get() = File("src/main/res/values/strings.xml").readText()

    private fun source(path: String): JSONObject = JSONObject(File(path).readText()).getJSONObject("strings")

    /** The body of one function of AppModel: from its signature to its closing brace (or the next declaration). */
    private fun body(signature: String): String {
        val after = appModel.substringAfter(signature)
        val end = listOf("\n    }", "\n    fun ", "\n    override fun ", "\n    private fun ", "\n    // ")
            .map { after.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return after
        return after.substring(0, end)
    }

    @Test fun theQuestionCardIsNotDroppedOnSendAlone() {
        val send = body("fun answerQuestion(")
        assertFalse("sending is not the answer: the card waits for approvalResolved", send.contains("removeQuestion"))
        // It goes when the computer settles it, and when a refusal says it is gone.
        assertTrue(appModel.contains("override fun onApprovalResolved(fingerprint: String)"))
        assertTrue(body("override fun onApprovalResolved(fingerprint: String)").contains("removeQuestion(fingerprint)"))
        assertTrue(body("private fun answerRefused(").contains("removeQuestion("))
    }

    @Test fun aRefusedApprovalIsNotStuckInSending() {
        // The decisions are behavioural now (RefusalsTest, Refusals.plan); AppModel only applies the plan.
        val refused = body("private fun answerRefused(")
        assertTrue(refused.contains("Refusals.plan("))
        assertTrue(refused.contains("outcomes.refusedButPending("))
        assertTrue(refused.contains("outcomes.refused("))
        assertTrue(refused.contains("removeApproval("))
    }

    @Test fun everyRefusalHasKenaisOwnShortSentenceInBothLanguages() {
        val english = source("../i18n/app-strings.json")
        val spanish = source("../i18n/app-strings.es.json")
        for (r in Refusal.values()) {
            val key = "refused_" + r.name.lowercase()
            assertTrue("$key is missing in English", english.has(key) && english.getString(key).isNotBlank())
            assertTrue("$key is missing in Spanish", spanish.has(key) && spanish.getString(key).isNotBlank())
            assertTrue("$key must be short enough for Home's one line", english.getString(key).length <= 120)
            assertTrue("AppModel shows $key", appModel.contains("R.string.$key"))
            assertTrue("$key is in the generated resources", strings.contains("""<string name="$key">"""))
        }
        assertTrue("every refusal AppModel speaks about is one Kenai knows", appModel.contains("fun refusalSentence(r: Refusal)"))
    }

    @Test fun aRefusalIsHandledByKenaiAndNotShownAsTheEnginesWords() {
        // The link only calls the refusal back for a code it understands, and never also reports it as a link error.
        assertTrue(linkClient.contains("Refusals.of(msg.code)"))
        assertTrue(linkClient.contains("Refusals.keeps("))
        assertTrue("a chat frame's `bad_request` is still a link error", linkClient.contains("Refusals.onlyAnswers("))
        assertTrue(appModel.contains("override fun onAnswerRefused(fingerprint: String?, refusal: Refusal)"))
        // The engine's own sentence is never the answer's sentence.
        assertFalse(appModel.contains("refusal.message"))
    }

    @Test fun theAuthAndSendErrorPathsAreUnchanged() {
        val onError = body("override fun onError(code: String, message: String)")
        assertTrue(onError.contains("if (code == \"auth\") context.getString(R.string.msg_rejected)"))
        assertTrue(onError.contains("R.string.msg_link_error"))
        assertTrue(onError.contains("if (code == \"send\" && outcome?.kind == OutcomeKind.FAILED) return@post"))
    }
}
