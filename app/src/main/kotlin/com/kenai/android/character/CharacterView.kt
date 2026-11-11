package com.kenai.android.character

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kenai.android.R
import com.kenai.android.character.CharacterPainter.drawCharacter

/**
 * The character, Koda, animated while it is on screen: the engine advances every frame and the picture is redrawn only when
 * the sprite cell (or the agent's colour) changes. With "remove animations" turned on Koda holds the first frame of its pose;
 * reactions still change the pose and end on time.
 */
@Composable
fun CharacterView(
    engine: CharacterEngine,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val sheet = ImageBitmap.imageResource(R.drawable.koda_chibi)
    val ctx = LocalContext.current
    val reduce =
        remember {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    var shown by remember(engine) { mutableStateOf<Any?>(null) }
    LaunchedEffect(engine, reduce) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) engine.update((now - last) / 1e9)
                last = now
                shown = Triple(engine.pose(), engine.frame(reduce), engine.bodyColor)
            }
        }
    }
    val sem = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(sem)) {
        shown // read, so a new cell redraws
        drawCharacter(engine, sheet, reduce)
    }
}
