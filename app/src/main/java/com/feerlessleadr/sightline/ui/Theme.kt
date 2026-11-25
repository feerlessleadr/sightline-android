package com.feerlessleadr.sightline.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The web app's colours (dark only, like the web app). Names follow its design tokens: background, surface,
 * line, text, muted, faint, accent, live red, and one colour per kind of alert.
 */
object Sl {
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF14181D)
    val Surface2 = Color(0xFF1C2229)
    val Line = Color(0xFF262E38)
    val Text = Color(0xFFE8EDF2)
    val Muted = Color(0xFF8B97A5)
    val Faint = Color(0xFF5D6875)
    val Accent = Color(0xFF4CC2FF)
    val Live = Color(0xFFFF4D4D)

    val Person = Color(0xFFFFB547)
    val Vehicle = Color(0xFF5AA9FF)
    val Animal = Color(0xFF7BD88F)
    val Delivery = Color(0xFFC792EA)
    val Motion = Color(0xFF9AA5B1)
}

private val SlColors = darkColorScheme(
    primary = Sl.Accent,
    onPrimary = Color(0xFF00212F),
    primaryContainer = Sl.Surface2,
    onPrimaryContainer = Sl.Accent,
    secondary = Sl.Muted,
    onSecondary = Sl.Background,
    secondaryContainer = Sl.Surface2,
    onSecondaryContainer = Sl.Text,
    background = Sl.Background,
    onBackground = Sl.Text,
    surface = Sl.Surface,
    onSurface = Sl.Text,
    surfaceVariant = Sl.Surface2,
    onSurfaceVariant = Sl.Muted,
    surfaceTint = Sl.Accent,
    surfaceContainerLowest = Sl.Background,
    surfaceContainerLow = Sl.Surface,
    surfaceContainer = Sl.Surface,
    surfaceContainerHigh = Sl.Surface2,
    surfaceContainerHighest = Sl.Surface2,
    outline = Sl.Faint,
    outlineVariant = Sl.Line,
    error = Sl.Live,
    onError = Color.White,
)

@Composable
fun SightlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SlColors, content = content)
}
