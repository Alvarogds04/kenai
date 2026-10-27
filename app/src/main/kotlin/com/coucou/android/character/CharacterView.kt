package com.coucou.android.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.coucou.android.character.CharacterPainter.drawCharacter

/** The character, animated every frame while it is on screen. */
@Composable
fun CharacterView(engine: CharacterEngine, modifier: Modifier = Modifier) {
    var t by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(engine) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) engine.update((now - last) / 1e9)
                last = now
                t = now / 1e9
            }
        }
    }
    Canvas(modifier) { drawCharacter(engine, t) }
}
