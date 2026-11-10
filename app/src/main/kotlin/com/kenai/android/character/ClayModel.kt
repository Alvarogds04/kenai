package com.kenai.android.character

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kenai's character: a soft clay body drawn from a few Fourier terms, two tall eyes and lids that close over them. Ported
 * from the Koda desktop's `frontend/clay-engine.js` (revision R7): the same poses, gestures, gaze, blinks and emotes, so
 * the phone and the computer show the same character. Pure: no Android, so the rules are tested on the JVM.
 *
 * Coordinates are the desktop's 200 x 200 view box; the painter scales them.
 */
object Clay {
    const val TAU = 2 * PI
    const val K = 5
    const val DIM = 2 * K + 1
    const val N = 144
    const val CX = 100.0
    const val CY = 102.0
    const val R0 = 54.0

    /** Index of the cos (or sin) coefficient of harmonic [k]; index 0 is unused. */
    fun ci(
        k: Int,
        sin: Boolean = false,
    ) = 2 * k - 1 + if (sin) 1 else 0

    fun clamp(
        v: Double,
        a: Double,
        b: Double,
    ) = max(a, min(b, v))

    fun approach(
        c: Double,
        t: Double,
        rate: Double,
        dt: Double,
    ) = c + (t - c) * (1 - exp(-rate * dt))

    fun smooth(x: Double) = x * x * (3 - 2 * x)

    private fun coeffs(vararg spec: Triple<Int, Double, Double>) =
        DoubleArray(DIM).also { v ->
            for ((k, a, b) in spec) {
                v[ci(k)] = a
                v[ci(k, true)] = b
            }
        }

    /** Eye: sx/sy size, uL/uR upper lid, tilt lid slope, uc lid curvature, l/bow lower lid, asym left/right offset. */
    data class Eye(
        val sx: Double = 1.0,
        val sy: Double = 1.0,
        val uL: Double = .04,
        val uR: Double = .04,
        val tilt: Double = 0.0,
        val uc: Double = 0.0,
        val l: Double = 0.0,
        val bow: Double = 0.0,
        val asym: Double = 0.0,
    )

    class Pose(
        val v: DoubleArray,
        val eye: Eye,
        val lift: Double,
        val lean: Double,
        val knead: Double,
    )

    private val IDLE_EYE = Eye()

    /** The desktop's poses, by the app's states (`thinking` is the desktop's `think`, `error` its `error`). */
    val POSES: Map<BotState, Pose> =
        mapOf(
            BotState.IDLE to Pose(coeffs(Triple(1, 0.0, .03), Triple(2, .035, 0.0), Triple(4, -.01, 0.0)), IDLE_EYE, 0.0, 0.0, .04),
            BotState.THINKING to
                Pose(
                    coeffs(Triple(1, 0.0, -.015), Triple(2, -.085, -.03), Triple(3, .012, 0.0)),
                    Eye(1.02, 1.02, .26, .03, .08, .1, .05, .2, .08),
                    -3.0,
                    0.0,
                    .036,
                ),
            BotState.ERROR to
                Pose(
                    coeffs(Triple(1, 0.0, .05), Triple(2, .015, -.05), Triple(3, -.012, 0.0)),
                    Eye(.94, .9, 0.0, .2, -.1, .12, .04, .2, -.14),
                    2.0,
                    -2.4,
                    .032,
                ),
            BotState.WORKING to
                Pose(
                    coeffs(Triple(1, 0.0, .015), Triple(2, .055, 0.0), Triple(3, .009, -.008)),
                    IDLE_EYE.copy(sy = 1.04, l = .04),
                    -1.0,
                    0.0,
                    .047,
                ),
            BotState.SEARCHING to
                Pose(
                    coeffs(Triple(1, 0.0, .02), Triple(2, -.035, .025)),
                    IDLE_EYE.copy(sx = 1.04, sy = 1.06, uL = .08, uR = .08),
                    -2.0,
                    1.0,
                    .032,
                ),
            BotState.APPROVAL to
                Pose(
                    coeffs(Triple(1, 0.0, -.01), Triple(2, -.025, .02)),
                    IDLE_EYE.copy(sx = 1.12, sy = 1.14, uL = 0.0, uR = 0.0),
                    -2.5,
                    1.4,
                    .03,
                ),
            BotState.QUESTION to
                Pose(coeffs(Triple(1, 0.0, .025), Triple(2, .012, -.035)), IDLE_EYE.copy(uL = .03, uR = .18, asym = .065), -1.0, -3.0, .03),
            BotState.FINISHED to
                Pose(
                    coeffs(Triple(1, 0.0, .03), Triple(2, .025, 0.0), Triple(4, -.009, 0.0)),
                    IDLE_EYE.copy(uL = .14, uR = .14),
                    1.5,
                    0.0,
                    .026,
                ),
            BotState.RATELIMIT to
                Pose(coeffs(Triple(1, 0.0, .055), Triple(2, .065, .018)), IDLE_EYE.copy(sy = .94, uL = .38, uR = .38), 3.0, -1.0, .024),
            BotState.SLEEPING to
                Pose(coeffs(Triple(1, 0.0, .07), Triple(2, .065, 0.0)), IDLE_EYE.copy(sy = .92, uL = .94, uR = .94), 4.0, 0.0, .012),
            BotState.DIZZY to
                Pose(
                    coeffs(Triple(1, 0.0, .045), Triple(2, -.015, .035)),
                    IDLE_EYE.copy(uL = .18, uR = .36, asym = .12, tilt = .08),
                    1.0,
                    0.0,
                    .036,
                ),
        )

    /** Additive offsets an emote puts over the pose; eye sx/sy are relative, the rest absolute. */
    data class Fx(
        val lift: Double = 0.0,
        val lean: Double = 0.0,
        val sx: Double = 0.0,
        val sy: Double = 0.0,
        val uL: Double = 0.0,
        val uR: Double = 0.0,
        val l: Double = 0.0,
        val tilt: Double = 0.0,
        val winkR: Double = 0.0,
    ) {
        operator fun times(w: Double) = Fx(lift * w, lean * w, sx * w, sy * w, uL * w, uR * w, l * w, tilt * w, winkR * w)

        operator fun plus(o: Fx) =
            Fx(
                lift + o.lift,
                lean + o.lean,
                sx + o.sx,
                sy + o.sy,
                uL + o.uL,
                uR + o.uR,
                l + o.l,
                tilt + o.tilt,
                winkR + o.winkR,
            )
    }

    /** An emote: [dur] seconds, [rise]/[fall] fractions of it, [motes] round sparks rising off the body. */
    class Emote(
        val dur: Double,
        val rise: Double,
        val fall: Double,
        val motes: Int,
        val fx: (Double) -> Fx,
    )

    val EMOTES: Map<BotEmote, Emote> =
        mapOf(
            BotEmote.LOVE to Emote(1.8, .09, .17, 4) { e -> Fx(lift = -2.5 * e, uL = -.04 * e, uR = -.04 * e, l = .1 * e) },
            BotEmote.SURPRISED to
                Emote(1.0, .14, .38, 0) { e -> Fx(lift = -4 * e, sx = .22 * e, sy = .3 * e, uL = -.04 * e, uR = -.04 * e) },
            BotEmote.PROUD to Emote(1.3, .17, .22, 3) { e -> Fx(lift = -2 * e, lean = 1.5 * e, uL = -.04 * e, uR = -.04 * e, l = .06 * e) },
            BotEmote.WINK to Emote(.8, .2, .3, 0) { e -> Fx(lean = 1.6 * e, winkR = .95 * e) },
            BotEmote.YAWN to Emote(1.0, .5, .5, 0) { e -> Fx(lift = -3 * e, uL = .55 * e, uR = .55 * e, sy = .08 * e) },
            BotEmote.HAPPY to Emote(1.0, .2, .4, 0) { e -> Fx(lift = -1.5 * e, l = .26 * e, sy = -.12 * e, sx = .1 * e) },
            BotEmote.ANNOYED to Emote(.8, .25, .3, 0) { e -> Fx(lean = -2 * e, uL = .3 * e, uR = .3 * e, tilt = .15 * e) },
        )

    /** 0 before and after, a smooth rise and fall, 1 in between. */
    fun envelope(
        em: Emote,
        p: Double,
    ): Double =
        when {
            p <= 0 || p >= 1 -> 0.0
            p < em.rise -> smooth(p / em.rise)
            p > 1 - em.fall -> smooth((1 - p) / em.fall)
            else -> 1.0
        }

    val COS: Array<DoubleArray> = Array(N) { i -> DoubleArray(K + 1) { k -> cos(k * i * TAU / N) } }
    val SIN: Array<DoubleArray> = Array(N) { i -> DoubleArray(K + 1) { k -> sin(k * i * TAU / N) } }

    /** One eye as drawn: centre, radii, rotation (degrees), upper lid closed fraction, its slope and curve, lower lid and bow. */
    data class EyeGeo(
        val cx: Double,
        val cy: Double,
        val rx: Double,
        val ry: Double,
        val rot: Double,
        val upper: Double,
        val slope: Double,
        val uc: Double,
        val lower: Double,
        val bow: Double,
    )

    data class Mote(
        val x: Double,
        val y: Double,
        val opacity: Double,
    )

    /** Everything the painter needs for one frame, in view-box units. */
    class Geometry(
        val pts: Array<DoubleArray>,
        val eyes: List<EyeGeo>,
        val motes: List<Mote>,
        val lift: Double,
    ) {
        val minX get() = pts.minOf { it[0] }
        val maxX get() = pts.maxOf { it[0] }
        val maxY get() = pts.maxOf { it[1] }
    }

    fun bump(
        p: Double,
        c: Double,
        w: Double,
    ) = max(0.0, 1 - abs(p - c) / w)

    fun sqrtOf(x: Double) = sqrt(x)
}
