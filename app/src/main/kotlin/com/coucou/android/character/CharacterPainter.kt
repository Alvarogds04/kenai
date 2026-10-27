package com.coucou.android.character

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Draws the placeholder character filling the scope (HU-049 T3): a disc in the state colour with two eyes. */
object CharacterPainter {
    fun DrawScope.drawCharacter(engine: CharacterEngine, tSec: Double) {
        val c = engine.color()
        val body = Color(c.r.toFloat(), c.g.toFloat(), c.b.toFloat())
        val r = size.minDimension * 0.42f * engine.breath(tSec).toFloat()
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(body, r, centre)
        val eye = Color(0xFF1B1D22)
        val closed = engine.state == BotState.SLEEPING
        val dx = r * 0.32f
        val ey = centre.y - r * 0.08f
        for (x in listOf(centre.x - dx, centre.x + dx)) {
            if (closed) drawLine(eye, Offset(x - r * 0.1f, ey), Offset(x + r * 0.1f, ey), strokeWidth = r * 0.06f)
            else drawOval(eye, Offset(x - r * 0.07f, ey - r * 0.11f), androidx.compose.ui.geometry.Size(r * 0.14f, r * 0.22f))
        }
    }
}
