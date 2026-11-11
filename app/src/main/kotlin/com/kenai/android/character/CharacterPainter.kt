package com.kenai.android.character

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Draws Kenai's character: Koda, one 32 x 32 cell of the Chibi sprite sheet (res/drawable-nodpi/koda_chibi.png), at a
 * whole-number scale with no filtering (crisp pixels), centred in its box. An optional soft radial light sits behind it.
 * The live views ([CharacterView], [KodaView]) and the widgets' still pictures (app/CharacterBitmap) all draw through here.
 */
object CharacterPainter {
    const val CELL = 32

    /** How strong the agent's light behind a live Koda is at its centre. */
    private const val AGENT_GLOW_ALPHA = .38f

    /** Koda in [pose], sheet column [frame], with [glow] (a colour fading out to the edge of the box) behind it when given. */
    fun DrawScope.drawKoda(
        sheet: ImageBitmap,
        pose: KodaPose,
        frame: Int,
        glow: Color? = null,
    ) {
        if (glow != null) {
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center, r), r, center)
        }
        val scale = (size.minDimension / CELL).toInt().coerceAtLeast(1)
        val side = CELL * scale
        drawImage(
            sheet,
            srcOffset = IntOffset(frame * CELL, pose.row * CELL),
            srcSize = IntSize(CELL, CELL),
            dstOffset = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt()),
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.None,
        )
    }

    /** The engine's Koda now: its pose and frame, and its agent's colour as the light behind it (none on a small one). */
    fun DrawScope.drawCharacter(
        engine: CharacterEngine,
        sheet: ImageBitmap,
        reduceMotion: Boolean = false,
    ) {
        val glow = engine.bodyColor?.takeIf { !engine.isMini }?.let { Color(it.r.toFloat(), it.g.toFloat(), it.b.toFloat(), AGENT_GLOW_ALPHA) }
        drawKoda(sheet, engine.pose(), engine.frame(reduceMotion), glow)
    }
}
