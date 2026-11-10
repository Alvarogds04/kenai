package com.kenai.android.character

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kenai.android.R

private const val CELL = 32

/**
 * Koda drawn from the sprite sheet at a whole-number scale (crisp pixels), centred in its box. With "remove animations"
 * turned on the cat holds the first frame of its pose.
 */
@Composable
fun KodaView(
    state: BotState,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val sheet = ImageBitmap.imageResource(R.drawable.koda_chibi)
    val pose = KodaPose.of(state)
    val ctx = LocalContext.current
    val reduce =
        remember {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    var t by remember(pose) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(pose, reduce) {
        if (reduce) return@LaunchedEffect
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                t = (now - start) / 1e9
            }
        }
    }
    val sem = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(sem)) {
        val scale = (minOf(size.width, size.height) / CELL).toInt().coerceAtLeast(1)
        val side = CELL * scale
        val dst = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt())
        drawImage(
            sheet,
            srcOffset = IntOffset(pose.frameAt(t, reduce) * CELL, pose.row * CELL),
            srcSize = IntSize(CELL, CELL),
            dstOffset = dst,
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.None,
        )
    }
}
