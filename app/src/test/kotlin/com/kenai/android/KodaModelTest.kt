package com.kenai.android

import com.kenai.android.character.BotEmote
import com.kenai.android.character.BotState
import com.kenai.android.character.KodaPose
import com.kenai.android.character.KodaRole
import com.kenai.android.core.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Koda (HU-055): each Kenai state maps to one cat pose and one role colour from design/system.md. */
class KodaModelTest {
    @Test fun everyBotStateHasAPose() {
        for (s in BotState.values()) KodaPose.of(s)
        assertEquals(KodaPose.REPOSO, KodaPose.of(BotState.IDLE))
        assertEquals(KodaPose.TRABAJA, KodaPose.of(BotState.WORKING))
        assertEquals(KodaPose.ESPERA, KodaPose.of(BotState.APPROVAL))
        assertEquals(KodaPose.TERMINO, KodaPose.of(BotState.FINISHED))
        assertEquals(KodaPose.ERROR, KodaPose.of(BotState.ERROR))
    }

    @Test fun everyEmoteHasTheNearestPose() {
        // A pet (love) and a wink: the attentive, content cat; happy and proud: the done pose; surprised: the "?" pose;
        // a yawn: asleep; a slap (annoyed): the flat-eared error pose.
        val expected = mapOf(
            BotEmote.LOVE to KodaPose.ESCUCHA, BotEmote.WINK to KodaPose.ESCUCHA,
            BotEmote.HAPPY to KodaPose.TERMINO, BotEmote.PROUD to KodaPose.TERMINO,
            BotEmote.SURPRISED to KodaPose.ESPERA, BotEmote.YAWN to KodaPose.REPOSO, BotEmote.ANNOYED to KodaPose.ERROR,
        )
        assertEquals(BotEmote.entries.toSet(), expected.keys)
        for ((e, p) in expected) assertEquals(e.name, p, KodaPose.of(e))
        assertEquals("dizzy shows the error pose", KodaPose.ERROR, KodaPose.of(BotState.DIZZY))
    }

    @Test fun framesStayInTheFourColumnsOfTheSheet() {
        for (p in KodaPose.values()) {
            assertTrue(p.name, p.row in 0..6)
            for (i in 0 until p.sequence.size) assertTrue("${p.name}[$i]", p.sequence[i] in 0..3)
        }
    }

    @Test fun frameAdvancesAtEightFramesPerSecondAndLoops() {
        val p = KodaPose.TRABAJA // sequence 0,0,1,1,2,2,3,3
        assertEquals(0, p.frameAt(0.0))
        assertEquals(1, p.frameAt(2 / 8.0 + 0.001))
        assertEquals(3, p.frameAt(7 / 8.0 + 0.001))
        assertEquals(0, p.frameAt(1.0 + 0.001)) // wraps
    }

    @Test fun reducedMotionHoldsTheFirstFrame() {
        for (p in KodaPose.values()) assertEquals(p.sequence[0], p.frameAt(3.7, reduceMotion = true))
    }

    @Test fun roleColoursAreTheDesktopSystemOnes() {
        assertEquals(0xFFB8FF2EL, KodaRole.GO)
        assertEquals(0xFFFFE100L, KodaRole.SUN)
        assertEquals(0xFF2EB4FFL, KodaRole.LIVE)
        assertEquals(0xFFFF5C75L, KodaRole.RED)
        assertEquals(0xFF6F6D66L, KodaRole.FAINT)
    }

    @Test fun roleOfEachStateFollowsTheSystem() {
        assertEquals(KodaRole.SUN, KodaRole.of(BotState.APPROVAL))
        assertEquals(KodaRole.SUN, KodaRole.of(BotState.QUESTION))
        assertEquals(KodaRole.LIVE, KodaRole.of(BotState.WORKING))
        assertEquals(KodaRole.GO, KodaRole.of(BotState.FINISHED))
        assertEquals(KodaRole.RED, KodaRole.of(BotState.ERROR))
        assertEquals(KodaRole.FAINT, KodaRole.of(BotState.IDLE))
    }

    @Test fun inkOnARoleFillIsReadable() {
        for (r in listOf(KodaRole.GO, KodaRole.SUN, KodaRole.LIVE, KodaRole.RED)) {
            assertTrue(r.toString(16), Palette.contrast(KodaRole.ON_ROLE, r) >= 4.5)
        }
    }
}
