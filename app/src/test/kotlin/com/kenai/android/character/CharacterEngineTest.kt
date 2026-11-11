package com.kenai.android.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's character engine (S5): Koda's pose and frame for a state or a reaction, with the slaps and emotes of HU-049 T6. */
class CharacterEngineTest {
    private var ms = 0.0

    private fun engine(state: BotState = BotState.IDLE) = CharacterEngine({ ms }).apply { setState(state, force = true) }

    private fun CharacterEngine.run(seconds: Double): CharacterEngine {
        repeat((seconds * 60).toInt()) {
            ms += 1000.0 / 60
            update(1.0 / 60)
        }
        return this
    }

    @Test fun everyStateShowsItsKodaPose() {
        for (s in BotState.entries) assertEquals("$s", KodaPose.of(s), engine(s).run(.5).pose())
    }

    @Test fun theFrameFollowsThePoseSequenceAndHoldsTheFirstWithReducedMotion() {
        val e = engine(BotState.WORKING)
        val seen = (0 until 60).map { e.run(1.0 / 60).frame() }.toSet()
        assertTrue("the working pose animates", seen.size > 1)
        assertTrue(seen.all { it in 0..3 })
        e.run(.37)
        assertEquals(KodaPose.TRABAJA.sequence[0], e.frame(reduceMotion = true))
    }

    @Test fun aNewStateStartsItsPoseFromTheFirstFrame() {
        val e = engine(BotState.IDLE).run(2.3)
        e.setState(BotState.FINISHED)
        assertEquals(KodaPose.TERMINO, e.pose())
        assertEquals(KodaPose.TERMINO.sequence[0], e.frame())
        // The same state again (forced or not) does not restart it.
        e.run(.8)
        val f = e.frame()
        e.setState(BotState.FINISHED, force = true)
        assertEquals(f, e.frame())
    }

    @Test fun anEmotePlaysOnceOverTheStateAndEnds() {
        val e = engine(BotState.WORKING).run(1.0)
        assertTrue(e.triggerEmote(BotEmote.SURPRISED))
        assertFalse("one at a time, never queued", e.triggerEmote(BotEmote.HAPPY))
        assertEquals(BotEmote.SURPRISED, e.currentEmote())
        assertEquals(KodaPose.ESPERA, e.pose())
        e.run(1.1)
        assertNull(e.currentEmote())
        assertEquals("back to the state's pose", KodaPose.TRABAJA, e.pose())
        assertTrue(e.triggerEmote(BotEmote.HAPPY))
    }

    @Test fun aPetIsTheContentCatForItsLength() {
        val e = engine().run(1.0)
        e.triggerEmote(BotEmote.LOVE)
        assertEquals(KodaPose.ESCUCHA, e.run(1.5).pose())
        assertEquals(KodaPose.REPOSO, e.run(.5).pose())
    }

    @Test fun aNewStateCancelsTheReaction() {
        val e = engine().run(1.0)
        e.triggerEmote(BotEmote.LOVE)
        e.setState(BotState.APPROVAL)
        assertNull(e.currentEmote())
        assertEquals(KodaPose.ESPERA, e.pose())
    }

    @Test fun aPermanentEmoteNeverEnds() {
        val e = engine().apply { setPermanentEmote(BotEmote.YAWN) }.run(5.0)
        assertEquals(BotEmote.YAWN, e.currentEmote())
        assertEquals(KodaPose.REPOSO, e.pose())
        e.setPermanentEmote(BotEmote.ANNOYED)
        assertEquals(KodaPose.ERROR, e.pose())
    }

    @Test fun threeQuickSlapsMakeItDizzyButSlowOnesDoNot() {
        var dizzy = 0
        val e = engine().apply { onDizzy = { dizzy++ } }
        repeat(3) {
            e.slap()
            ms += 300
        }
        assertEquals(1, dizzy)
        repeat(3) {
            e.slap()
            ms += 1_500
        }
        assertEquals(1, dizzy)
    }

    @Test fun aSlapAnnoysItForAMomentWithTheFlatEars() {
        val sounds = mutableListOf<String>()
        val e = CharacterEngine({ ms }, sound = { sounds += it }).apply { setState(BotState.IDLE, force = true) }.run(1.0)
        e.slap()
        assertEquals(listOf("slap"), sounds)
        assertEquals(BotEmote.ANNOYED, e.currentEmote())
        assertEquals(KodaPose.ERROR, e.pose())
        e.run(1.0)
        assertNull(e.currentEmote())
        assertEquals(KodaPose.REPOSO, e.pose())
    }

    @Test fun dizzyIsTheErrorPose() {
        assertEquals(KodaPose.ERROR, engine(BotState.DIZZY).pose())
    }

    @Test fun itGlowsInItsAgentsColourElseItsStatesRoleColour() {
        val e = engine(BotState.WORKING)
        val live = KodaRole.LIVE
        assertEquals(Rgb(((live shr 16) and 0xFF) / 255.0, ((live shr 8) and 0xFF) / 255.0, (live and 0xFF) / 255.0), e.color())
        val blue = Rgb(.2, .4, 1.0)
        e.bodyColor = blue
        assertEquals(blue, e.color())
    }

    @Test fun aLongPauseDoesNotJump() {
        val e = engine().run(1.0)
        e.triggerEmote(BotEmote.ANNOYED) // 0.8 s
        e.update(10.0) // the screen was away: one capped step, not ten seconds at once
        assertEquals(BotEmote.ANNOYED, e.currentEmote())
    }
}
