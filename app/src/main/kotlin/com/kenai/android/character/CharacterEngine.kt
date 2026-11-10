package com.kenai.android.character

import com.kenai.android.character.Clay.DIM
import com.kenai.android.character.Clay.K
import com.kenai.android.character.Clay.N
import com.kenai.android.character.Clay.TAU
import com.kenai.android.character.Clay.approach
import com.kenai.android.character.Clay.ci
import com.kenai.android.character.Clay.clamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One Kenai character on screen: the desktop's clay engine (see [Clay]) stepped by [update] and read by [geometry].
 * Screens set the agent's [state]; a tap is a [slap] (three quick ones make it dizzy), a long press an emote.
 *
 * [clock] gives milliseconds and only times the slaps; animation time is what [update] adds up, so a widget can settle a
 * still picture with fake time. [random] fixes the kneading and gaze, so a seed always gives the same picture.
 */
class CharacterEngine(
    private val clock: () -> Double,
    private val random: Random = Random.Default,
    private val sound: SoundSink? = null,
) {
    var state: BotState = BotState.IDLE
        private set

    /** The agent's colour; null draws Kenai's own green. */
    var bodyColor: Rgb? = null

    /** A small character in a list row: no motes. */
    var isMini = false

    /** Called when three quick slaps make the character dizzy. */
    var onDizzy: (() -> Unit)? = null

    private fun rand(
        a: Double,
        b: Double,
    ) = a + random.nextDouble() * (b - a)

    private class Knead(
        val j: Int,
        val f: Double,
        val p: Double,
    )

    private val knead = List(2 * (DIM - 3)) { i -> Knead(3 + i % (DIM - 3), rand(.11, .3), rand(0.0, TAU)) }

    private var t = 0.0
    private val x =
        Clay.POSES
            .getValue(BotState.IDLE)
            .v
            .copyOf()
    private var kneadAmp = Clay.POSES.getValue(BotState.IDLE).knead
    private var eye = Clay.POSES.getValue(BotState.IDLE).eye
    private var lift = 0.0
    private var lean = 0.0
    private var shakeX = 0.0
    private var nodY = 0.0
    private var tiltB = 0.0
    private var gestureT0 = 0.0
    private var gazeX = 0.0
    private var gazeY = 0.0
    private var gazeTX = 0.0
    private var gazeTY = 0.0
    private var nextGaze = 1.0
    private var blinkT0 = -1.0
    private var blinkDur = .16
    private var nextBlink = 1.6

    private class Reaction(
        val emote: BotEmote,
        val t0: Double,
    )

    private var reaction: Reaction? = null
    private var ghost: Pair<Reaction, Double>? = null // the cancelled reaction and its fading weight
    private var permanentEmote: BotEmote? = null
    private val slaps = ArrayDeque<Double>()

    fun setState(
        s: BotState,
        force: Boolean = false,
    ) {
        if (s == state && !force) return
        if (s != state) {
            cancelReaction()
            gestureT0 = t
        }
        state = s
        nextGaze = t
    }

    /** An emote that never ends (the gallery shows each one). */
    fun setPermanentEmote(e: BotEmote?) {
        permanentEmote = e
    }

    /** Plays an emote once over the current pose; one at a time, never queued. */
    fun triggerEmote(e: BotEmote): Boolean {
        if (reaction != null) return false
        reaction = Reaction(e, t)
        return true
    }

    /** The emote showing now, if any. */
    fun currentEmote(): BotEmote? = reaction?.emote ?: permanentEmote

    /** A tap: annoyed for a moment. Three within [DIZZY_WINDOW_MS] make the character dizzy ([onDizzy]). */
    fun slap() {
        val now = clock()
        sound?.play("slap")
        cancelReaction()
        triggerEmote(BotEmote.ANNOYED)
        slaps.addLast(now)
        while (slaps.isNotEmpty() && now - slaps.first() > DIZZY_WINDOW_MS) slaps.removeFirst()
        if (slaps.size >= DIZZY_SLAPS) {
            slaps.clear()
            onDizzy?.invoke()
        }
    }

    /** The body's main colour now. */
    fun color(): Rgb = bodyColor ?: KENAI_GREEN

    private fun progress(r: Reaction) = (t - r.t0) / Clay.EMOTES.getValue(r.emote).dur

    private fun cancelReaction() {
        val r = reaction ?: return
        if (progress(r) < 1) ghost = r to 1.0
        reaction = null
    }

    /** Advances the animation by [dt] seconds (capped, so a paused screen does not jump). */
    fun update(dt: Double) {
        val d = clamp(dt, 0.0, 1.0 / 20)
        t += d
        val pose = Clay.POSES.getValue(state)
        val g = t - gestureT0
        var shake = 0.0
        var nod = 0.0
        var tilt = 0.0
        when (state) {
            BotState.ERROR -> {
                tilt = .014 * sin(TAU * .32 * g)
                shake = .9 * sin(TAU * .32 * g + 1)
            }

            BotState.WORKING -> {
                nod = -.9 * sin(t * 4.4)
            }

            BotState.APPROVAL -> {
                nod = -.8 * abs(sin(t * 5.2))
            }

            BotState.QUESTION -> {
                tilt = -.014
            }

            BotState.FINISHED -> {
                if (g < 1.8) nod = 1.2 * sin(PI * g / 1.8)
            }

            BotState.SLEEPING -> {
                nod = .65 * sin(t * 1.8)
            }

            BotState.DIZZY -> {
                shake = 2.4 * sin(g * 9) * exp(-g / 3.3)
                tilt = .025 * sin(g * 7)
            }

            else -> {}
        }
        reaction?.let { if (progress(it) >= 1) reaction = null }
        ghost = ghost?.let { (r, w) -> (w - d / FADE_S).takeIf { it > 0 }?.let { r to it } }
        shakeX = approach(shakeX, shake, 14.0, d)
        nodY = approach(nodY, nod, 14.0, d)
        tiltB = approach(tiltB, tilt, 6.0, d)
        for (j in 1 until DIM) x[j] = approach(x[j], pose.v[j], 1.7, d)
        kneadAmp = approach(kneadAmp, pose.knead, 1.7, d)
        lift = approach(lift, pose.lift, 2.2, d)
        lean = approach(lean, pose.lean, 2.2, d)
        val e = pose.eye
        eye =
            Clay.Eye(
                approach(eye.sx, e.sx, 3.2, d),
                approach(eye.sy, e.sy, 3.2, d),
                approach(eye.uL, e.uL, 3.2, d),
                approach(eye.uR, e.uR, 3.2, d),
                approach(eye.tilt, e.tilt, 3.2, d),
                approach(eye.uc, e.uc, 3.2, d),
                approach(eye.l, e.l, 3.2, d),
                approach(eye.bow, e.bow, 3.2, d),
                approach(eye.asym, e.asym, 3.2, d),
            )
        if (t >= nextGaze) {
            when (state) {
                BotState.THINKING -> {
                    gazeTX = rand(.45, .95)
                    gazeTY = rand(-1.0, -.7)
                    nextGaze = t + rand(1.4, 2.8)
                }

                BotState.ERROR -> {
                    gazeTX = rand(-.9, -.3)
                    gazeTY = rand(-.1, .45)
                    nextGaze = t + rand(1.6, 3.0)
                }

                else -> {
                    if (random.nextDouble() < .3) {
                        gazeTX = 0.0
                        gazeTY = 0.0
                    } else {
                        gazeTX = rand(-1.0, 1.0)
                        gazeTY = rand(-.5, .45)
                    }
                    nextGaze = t + rand(1.2, 3.0)
                }
            }
        }
        when (state) {
            BotState.SEARCHING -> {
                gazeTX = sin(t * 2.6) * .8
                gazeTY = -.06
            }

            BotState.SLEEPING -> {
                gazeTX = 0.0
                gazeTY = .1
            }

            BotState.DIZZY -> {
                gazeTX = -.35 * sin(t * 9)
                gazeTY = .15
            }

            else -> {}
        }
        gazeX = approach(gazeX, gazeTX - .25 * shakeX / 3.6, 9.0, d)
        gazeY = approach(gazeY, gazeTY, 9.0, d)
        if (blinkT0 < 0 && t >= nextBlink && eye.l < .35) {
            blinkT0 = t
            blinkDur = if (state == BotState.THINKING) .24 else .16
        }
        if (blinkT0 >= 0 && t - blinkT0 > blinkDur + .04) {
            blinkT0 = -1.0
            val double = if (state == BotState.ERROR) .6 else .14
            nextBlink = if (random.nextDouble() < double) t + .17 else t + rand(2.4, 5.2) * if (state == BotState.THINKING) 1.4 else 1.0
        }
    }

    private fun fxOf(
        r: Reaction,
        p: Double,
    ): Clay.Fx {
        val em = Clay.EMOTES.getValue(r.emote)
        return em.fx(Clay.envelope(em, p))
    }

    private fun lid(side: Int, f: Clay.Fx) = if (side < 0) eye.uL + f.uL else eye.uR + f.uR

    /** The frame to draw now, in the desktop's 200 x 200 view box. */
    fun geometry(): Clay.Geometry {
        val c = x.copyOf()
        for (n in knead) c[n.j] += kneadAmp * .55 * sin(TAU * n.f * t + n.p) / sqrt(ceil(n.j / 2.0))
        c[ci(2)] += .012 * nodY
        c[ci(2, true)] += .006 * shakeX + tiltB
        val r = DoubleArray(N)
        var area = 0.0
        for (i in 0 until N) {
            var v = 1.0
            for (k in 1..K) v += c[ci(k)] * Clay.COS[i][k] + c[ci(k, true)] * Clay.SIN[i][k]
            r[i] = clamp(v, .7, 1.35)
            area += r[i] * r[i]
        }
        area *= .5 * TAU / N
        val scale = Clay.R0 * sqrt(PI / area) * (1 + .012 * sin(TAU * t / 4.2))

        var rf: Clay.Fx? = reaction?.let { fxOf(it, progress(it)) }
        permanentEmote?.let { rf = (rf ?: Clay.Fx()) + Clay.EMOTES.getValue(it).fx(1.0) }
        ghost?.let { (gr, w) -> rf = (rf ?: Clay.Fx()) + fxOf(gr, progress(gr).coerceAtMost(.999)) * Clay.smooth(w) }
        val f = rf ?: Clay.Fx()

        val ox = Clay.CX + shakeX + lean + f.lean
        val oy = Clay.CY + lift + nodY + f.lift
        val pts = Array(N) { i -> doubleArrayOf(ox + scale * r[i] * Clay.COS[i][1], oy + scale * r[i] * Clay.SIN[i][1]) }

        val sx = eye.sx * (1 + f.sx)
        val sy = eye.sy * (1 + f.sy)
        val fx = ox + gazeX * 3.2 + shakeX * .4 + lean * .5
        val fy = oy - 8 + gazeY * 2.4 + nodY * .6
        val closed = if (blinkT0 >= 0) ((t - blinkT0) / blinkDur).let { p -> if (p > 0 && p < 1) sin(p * PI) else 0.0 } else 0.0
        val eyes =
            listOf(-1, 1).map { side ->
                Clay.EyeGeo(
                    cx = fx + side * 16.5 + gazeX * 2.2,
                    cy = fy + side * eye.asym * -6,
                    rx = 6 * sx,
                    ry = 14.2 * sy * if (side < 0) 1 - eye.asym else 1 + eye.asym * .4,
                    rot = side * -3.0,
                    upper = clamp(max(max(lid(side, f), closed * .98), if (side > 0) f.winkR else 0.0), 0.0, 1.0),
                    slope = side * (eye.tilt + f.tilt),
                    uc = eye.uc,
                    lower = clamp(eye.l + f.l, 0.0, .9),
                    bow = eye.bow,
                )
            }
        val motes = ArrayList<Clay.Mote>()
        val re = reaction
        if (re != null && !isMini) {
            val em = Clay.EMOTES.getValue(re.emote)
            for (i in 0 until em.motes) {
                val a = t - re.t0 - i * .18
                if (a <= 0 || a >= MOTE_LIFE) continue
                val q = a / MOTE_LIFE
                val side = if (i % 2 == 1) 1 else -1
                motes += Clay.Mote(ox + side * (14 + 5 * i) + 4 * sin(q * 5 + i), oy - 30 - 22 * q, sin(PI * q) * .85)
            }
        }
        return Clay.Geometry(pts, eyes, motes, lift)
    }

    companion object {
        /** Kenai's own green (the desktop's skin gradient middle stop, #8FCB78). */
        val KENAI_GREEN = Rgb(0x8F / 255.0, 0xCB / 255.0, 0x78 / 255.0)
        const val DIZZY_WINDOW_MS = 1_200.0
        const val DIZZY_SLAPS = 3
        private const val FADE_S = .25
        private const val MOTE_LIFE = .9
    }
}
