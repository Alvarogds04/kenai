package com.abito.android.character

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Draws Abito's character as the desktop draws it (`clay-engine.js`): a soft shadow, the clay body with a light radial
 * gradient, two superellipse eyes and the lids that cover them. Geometry comes from [CharacterEngine.geometry].
 */
object CharacterPainter {
    private val GREEN_LIGHT = Color(0xFFACDD96)
    private val GREEN = Color(0xFF8FCB78)
    private val GREEN_DARK = Color(0xFF78B562)
    private val EYE = Color(0xFF0F1D0B)
    private val SHADE = Color(0xFF2D4A22)

    private fun Rgb.color() = Color(r.toFloat(), g.toFloat(), b.toFloat())

    /** The three skin stops: Abito's own green, or the agent's colour lightened and darkened the same way. */
    fun skin(engine: CharacterEngine): List<Color> {
        val c = engine.bodyColor ?: return listOf(GREEN_LIGHT, GREEN, GREEN_DARK)
        val white = Rgb(1.0, 1.0, 1.0)
        val black = Rgb(0.0, 0.0, 0.0)
        return listOf(c.mix(white, .22).color(), c.color(), c.mix(black, .14).color())
    }

    /** [tSec] is kept for callers; the engine keeps its own time. */
    fun DrawScope.drawCharacter(engine: CharacterEngine, @Suppress("UNUSED_PARAMETER") tSec: Double = 0.0) {
        val g = engine.geometry()
        val k = size.minDimension / 200f
        translate((size.width - 200f * k) / 2f, (size.height - 200f * k) / 2f) {
            scale(k, k, pivot = Offset.Zero) {
                drawClay(g, skin(engine), engine.isMini)
            }
        }
    }

    private fun DrawScope.drawClay(g: Clay.Geometry, skin: List<Color>, mini: Boolean) {
        val minX = g.minX.toFloat()
        val maxX = g.maxX.toFloat()
        val maxY = g.maxY.toFloat()
        if (!mini) {
            val rx = (maxX - minX) * .44f
            val cy = min(192f, maxY + 8f)
            val alpha = Clay.clamp(1 + g.lift / 14, .5, 1.0).toFloat()
            drawOval(
                Brush.radialGradient(listOf(SHADE.copy(alpha = .2f * alpha), SHADE.copy(alpha = 0f)), Offset(100f, cy), max(rx, 1f)),
                topLeft = Offset(100f - rx, cy - 7f),
                size = Size(2 * rx, 14f),
            )
        }
        val body = bodyPath(g.pts)
        val minY = g.pts.minOf { it[1] }.toFloat()
        val w = maxX - minX
        val h = maxY - minY
        drawPath(
            body,
            Brush.radialGradient(
                0f to skin[0], .55f to skin[1], 1f to skin[2],
                center = Offset(minX + .36f * w, minY + .28f * h),
                radius = .95f * max(w, h),
            ),
        )
        for (e in g.eyes) {
            translate(e.cx.toFloat(), e.cy.toFloat()) {
                rotate(e.rot.toFloat(), pivot = Offset.Zero) {
                    clipPath(lidRegion(e)) { drawPath(superellipse(e.rx, e.ry), EYE) }
                }
            }
        }
        for (m in g.motes) drawCircle(skin[0].copy(alpha = m.opacity.toFloat()), 2.4f, Offset(m.x.toFloat(), m.y.toFloat()))
    }

    /** A closed smooth curve through every point (Catmull-Rom as cubic Béziers), like the desktop's `pathFrom`. */
    fun bodyPath(p: Array<DoubleArray>): Path {
        val n = p.size
        val path = Path()
        path.moveTo(p[0][0].toFloat(), p[0][1].toFloat())
        for (i in 0 until n) {
            val p0 = p[(i - 1 + n) % n]
            val p1 = p[i]
            val p2 = p[(i + 1) % n]
            val p3 = p[(i + 2) % n]
            path.cubicTo(
                (p1[0] + (p2[0] - p0[0]) / 6).toFloat(), (p1[1] + (p2[1] - p0[1]) / 6).toFloat(),
                (p2[0] - (p3[0] - p1[0]) / 6).toFloat(), (p2[1] - (p3[1] - p1[1]) / 6).toFloat(),
                p2[0].toFloat(), p2[1].toFloat(),
            )
        }
        path.close()
        return path
    }

    /** A rounded-square eye of half-width [rx] and half-height [ry] (superellipse, exponent 4.2), centred on the origin. */
    fun superellipse(rx: Double, ry: Double, n: Double = 4.2): Path {
        val s = min(1.0, (8 * 2.0.pow(-1 / n) - 4) / 3)
        val l = rx * s
        val m = ry * s
        val u = listOf(
            rx to 0.0, rx to m, l to ry, 0.0 to ry, -l to ry, -rx to m, -rx to 0.0, -rx to -m, -l to -ry, 0.0 to -ry, l to -ry, rx to -m, rx to 0.0,
        )
        val path = Path()
        path.moveTo(u[0].first.toFloat(), u[0].second.toFloat())
        var i = 1
        while (i < 13) {
            path.cubicTo(
                u[i].first.toFloat(), u[i].second.toFloat(), u[i + 1].first.toFloat(), u[i + 1].second.toFloat(),
                u[i + 2].first.toFloat(), u[i + 2].second.toFloat(),
            )
            i += 3
        }
        path.close()
        return path
    }

    /** The part of the eye left open between the upper lid (closed fraction, slope, curve) and the lower lid (fraction, bow). */
    fun lidRegion(e: Clay.EyeGeo): Path {
        val w = e.rx + 2
        val h = 2 * e.ry + 2
        val top = -e.ry - 1
        val bot = e.ry + 1
        fun yu(x: Double) = top + e.upper * h + e.slope * x + e.uc * e.upper * h * (x / w).pow(2)
        fun yl(x: Double) = bot - e.lower * h * (1 - e.bow * (x / w).pow(2))
        val path = Path()
        for (k in 0..8) {
            val x = -w + 2 * w * k / 8
            if (k == 0) path.moveTo(x.toFloat(), yu(x).toFloat()) else path.lineTo(x.toFloat(), yu(x).toFloat())
        }
        for (k in 8 downTo 0) {
            val x = -w + 2 * w * k / 8
            path.lineTo(x.toFloat(), max(yl(x), yu(x) + .01).toFloat())
        }
        path.close()
        return path
    }
}
