package com.abito.android.character

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/** Abito's clay character, ported from the desktop's clay-engine.js (HU-049 T6). */
class CharacterEngineTest {
    private var ms = 0.0

    private fun engine(
        seed: Int = 7,
        state: BotState = BotState.IDLE,
    ) = CharacterEngine({ ms }, Random(seed)).apply { setState(state, force = true) }

    private fun CharacterEngine.run(seconds: Double): CharacterEngine {
        repeat((seconds * 60).toInt()) {
            ms += 1000.0 / 60
            update(1.0 / 60)
        }
        return this
    }

    /** Shoelace area of the body outline. */
    private fun area(g: Clay.Geometry): Double {
        var a = 0.0
        for (i in g.pts.indices) {
            val p = g.pts[i]
            val q = g.pts[(i + 1) % g.pts.size]
            a += p[0] * q[1] - q[0] * p[1]
        }
        return abs(a) / 2
    }

    @Test fun everyStateHasAPoseAndEveryEmoteAnAnimation() {
        assertEquals(BotState.entries.toSet(), Clay.POSES.keys)
        assertEquals(BotEmote.entries.toSet(), Clay.EMOTES.keys)
    }

    @Test fun theSameSeedGivesTheSamePicture() {
        val a = engine(seed = 3, state = BotState.WORKING).run(2.0).geometry()
        ms = 0.0
        val b = engine(seed = 3, state = BotState.WORKING).run(2.0).geometry()
        assertTrue(a.pts.indices.all { a.pts[it].contentEquals(b.pts[it]) })
        assertEquals(a.eyes, b.eyes)
    }

    @Test fun theBodyKeepsItsSizeInEveryState() {
        val expected = PI * Clay.R0 * Clay.R0
        for (s in BotState.entries) {
            val a = area(engine(state = s).run(3.0).geometry())
            assertEquals("$s area", 1.0, a / expected, .04)
        }
    }

    @Test fun asleepTheEyesAreClosedWaitingForADecisionTheyAreOpen() {
        val sleeping = engine(state = BotState.SLEEPING).run(4.0).geometry()
        assertTrue(sleeping.eyes.all { it.upper > .85 })
        val approval = engine(state = BotState.APPROVAL).run(4.0)
        val lids =
            (0 until 120).map {
                approval
                    .run(1.0 / 60)
                    .geometry()
                    .eyes
                    .maxOf { it.upper }
            }
        assertTrue("open most of the time", lids.count { it < .05 } > 100)
    }

    @Test fun aNewStateIsReachedGraduallyNotInOneFrame() {
        val e = engine(state = BotState.IDLE).run(3.0)
        val before = e.geometry().eyes[0].upper
        e.setState(BotState.SLEEPING)
        val next =
            e
                .run(1.0 / 60)
                .geometry()
                .eyes[0]
                .upper
        assertTrue("one frame moves a little", next - before < .1)
        assertTrue(
            "then it gets there",
            e
                .run(4.0)
                .geometry()
                .eyes[0]
                .upper > .85,
        )
    }

    @Test fun anEmotePlaysOnceAndEnds() {
        val e = engine().run(1.0)
        assertTrue(e.triggerEmote(BotEmote.SURPRISED))
        assertFalse("one at a time, never queued", e.triggerEmote(BotEmote.HAPPY))
        assertEquals(BotEmote.SURPRISED, e.currentEmote())
        e.run(1.1)
        assertNull(e.currentEmote())
        assertTrue(e.triggerEmote(BotEmote.HAPPY))
    }

    @Test fun loveSendsMotesUpExceptOnASmallCharacter() {
        val big =
            engine()
                .run(1.0)
                .apply { triggerEmote(BotEmote.LOVE) }
                .run(.4)
                .geometry()
        assertTrue(big.motes.isNotEmpty())
        val mini =
            engine()
                .run(1.0)
                .apply {
                    isMini = true
                    triggerEmote(BotEmote.LOVE)
                }.run(.4)
                .geometry()
        assertTrue(mini.motes.isEmpty())
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

    @Test fun aSlapAnnoysItForAMoment() {
        val e = engine().run(1.0)
        e.slap()
        assertEquals(BotEmote.ANNOYED, e.currentEmote())
        e.run(1.0)
        assertNull(e.currentEmote())
    }

    @Test fun itIsAbitosGreenUnlessItWearsItsAgentsColour() {
        val e = engine()
        assertEquals(CharacterEngine.ABITO_GREEN, e.color())
        val blue = Rgb(.2, .4, 1.0)
        e.bodyColor = blue
        assertEquals(blue, e.color())
    }

    @Test fun aLongPauseDoesNotJump() {
        val e = engine(state = BotState.IDLE).run(3.0)
        val before = e.geometry().eyes[0].upper
        e.setState(BotState.SLEEPING)
        e.update(10.0) // the screen was away: one capped step, not ten seconds at once
        assertTrue(e.geometry().eyes[0].upper - before < .2)
    }
}
