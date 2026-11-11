package com.kenai.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kenai.android.core.Palette
import com.kenai.android.core.StatusPalette
import com.kenai.android.core.Tokens

/**
 * The look of the Kenai website (docs/site.css): near-black page, slightly lighter cards with a thin
 * border, soft blue accent, rounded corners and pill-shaped buttons; in the light theme the desktop's warm
 * paper page with near-black ink. Kenai itself is untouched.
 */
private fun scheme(base: ColorScheme, p: Palette, t: Tokens): ColorScheme {
    // Surfaces and text come from the tokens, the accent from Palette.
    return base.copy(
        background = Color(t.bg), surface = Color(t.bg),
        surfaceVariant = Color(t.panel), surfaceContainer = Color(t.panel),
        onBackground = Color(t.text), onSurface = Color(t.text), onSurfaceVariant = Color(t.textDim),
        outline = Color(t.line), outlineVariant = Color(t.line),
        primary = Color(p.accent), onPrimary = Color(p.onAccent), error = Color(p.error),
    )
}

private val Dark = scheme(darkColorScheme(), Palette.DARK, Tokens.DARK)

// Dialogs sit on surfaceContainerHigh: the light card, not Material's lilac default.
private val Light = scheme(lightColorScheme(), Palette.LIGHT, Tokens.LIGHT).copy(surfaceContainerHigh = Color(Tokens.LIGHT.panel))

/** Link status colours (the dot next to "Connected"). */
object StatusColors {
    val online = Color(StatusPalette.ONLINE)
    val busy = Color(StatusPalette.BUSY)
    val offline = Color(StatusPalette.OFFLINE)
}

/** The app follows the system's dark/light mode; there is no setting of its own. */
@Composable
fun KenaiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTokens provides (if (dark) Tokens.DARK else Tokens.LIGHT)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}

/**
 * The surfaces that come from the island stay dark in both themes: the sheets, the scan screen and the Home hero.
 * Inside, the tokens, the Material colours and the default text colour are the dark theme's.
 */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTokens provides Tokens.DARK) {
        MaterialTheme(colorScheme = Dark) {
            CompositionLocalProvider(LocalContentColor provides Color(Tokens.DARK.text), content = content)
        }
    }
}

/** The gutter at the side of every screen, and the gap between cards (one rhythm everywhere). */
val Gutter = 16.dp
val Gap = 12.dp
