package com.kenai.android.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kenai.android.R
import com.kenai.android.core.WidgetTone
import com.kenai.android.character.BotState
import com.kenai.android.character.CharacterPainter.drawKoda
import com.kenai.android.character.KodaPose

/**
 * One still picture of Koda for a widget (a widget cannot animate): the first frame of the pose for [BotState] from the sprite
 * sheet, scaled by whole pixels with no filtering, so the same state always gives the same crisp picture. With [glow] a soft
 * radial light in the card's colour sits behind the cat. No timer and no frame loop; a few pictures are kept so a push that
 * changes only the text redraws nothing.
 */
object CharacterBitmap {
    private const val KEEP = 16

    /** How strong the glow is at its centre; a muted state glows less. */
    private const val GLOW_ALPHA = 0.55f
    private const val GLOW_ALPHA_MUTED = 0.32f

    private data class Key(val state: BotState, val size: Int, val glow: Boolean, val tone: WidgetTone)

    private val cache = object : LinkedHashMap<Key, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>) = size > KEEP
    }

    private var sheet: ImageBitmap? = null

    /** The sprite sheet, decoded once at its own size (it lives in drawable-nodpi, so nothing rescales it). */
    private fun sheet(context: Context): ImageBitmap =
        sheet ?: BitmapFactory.decodeResource(context.resources, R.drawable.koda_chibi, BitmapFactory.Options().apply { inScaled = false })
            .asImageBitmap().also { sheet = it }

    /**
     * Koda in [state], [sizePx] pixels square. The glow is in [tone], the colour of the card it sits on (by default the one of
     * its own state), so the Widget and its Koda always share one colour. The same instance comes back while it is kept.
     */
    @Synchronized
    fun of(context: Context, state: BotState, sizePx: Int, glow: Boolean = false, tone: WidgetTone = WidgetTone.of(state)): Bitmap {
        val key = Key(state, sizePx, glow, tone)
        return cache.getOrPut(key) { draw(sheet(context), state, sizePx, glow, tone) }
    }

    private fun draw(sheet: ImageBitmap, state: BotState, sizePx: Int, glow: Boolean, tone: WidgetTone): Bitmap {
        val pose = KodaPose.of(state)
        val image = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(sizePx.toFloat(), sizePx.toFloat())) {
            val light = if (glow) Color(tone.argb).copy(alpha = if (tone == WidgetTone.MUTED || tone == WidgetTone.IDLE) GLOW_ALPHA_MUTED else GLOW_ALPHA) else null
            drawKoda(sheet, pose, pose.frameAt(0.0, reduceMotion = true), light)
        }
        return image.asAndroidBitmap()
    }
}
