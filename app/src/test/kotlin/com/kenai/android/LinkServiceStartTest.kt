package com.kenai.android

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KN-04 live finding (19 Nov): Android denied `startForegroundService` when the process was started from a background receiver
 * («Background started FGS: Disallowed … uidState: RCVR»), the error was swallowed, and opening the app never retried because the
 * link already existed. The process then stayed cached and Android's firewall cut its network in the background.
 */
class LinkServiceStartTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()

    @Test fun showingTheAppStartsTheLinkServiceAgainWhenPaired() {
        val main = src("MainActivity.kt")
        val onStart = main.substringAfter("override fun onStart()").substringBefore("override fun onStop()")
        assertTrue("onStart must (re)start the foreground service: the activity is the one place Android allows it", onStart.contains("model.ensureLinkService()"))
        val model = src("app/AppModel.kt")
        val ensure = model.substringAfter("fun ensureLinkService()").substringBefore("\n    }")
        assertTrue("only while a computer is paired", ensure.contains("isPaired") && ensure.contains("LinkService.start("))
    }

    @Test fun aRefusedForegroundStartIsLoggedNotSwallowed() {
        val svc = src("app/LinkService.kt")
        val start = svc.substringAfter("fun start(context: Context)").substringBefore("fun stop(")
        assertTrue(start.contains("Log.w("))
    }
}
