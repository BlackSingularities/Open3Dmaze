package com.blacksingularities.open3dmaze.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Czarny interfejs AMOLED jak w OpenTimerze: czerń, biel i jeden akcent. */
object Palette {
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF0A0A0A)
    val PanelHigh = Color(0xFF161616)
    val Line = Color(0x24FFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Danger = Color(0xFFFF3B47)
    val Gold = Color(0xFFFFD54F)
    val Coffee = Color(0xFFF2C14E)
    val OnCoffee = Color(0xFF241A00)
}

data class Accent(val name: String, val color: Color)

val Accents = listOf(
    Accent("Biel", Color(0xFFFFFFFF)),
    Accent("Cyjan", Color(0xFF00E5FF)),
    Accent("Magenta", Color(0xFFFF2D95)),
    Accent("Limonka", Color(0xFFB6FF3B)),
    Accent("Bursztyn", Color(0xFFFFB300)),
    Accent("Fiolet", Color(0xFFA78BFA)),
    Accent("Koral", Color(0xFFFF6B5A)),
)

/** Wygląd kulki: kolor bazowy, połysk i opcjonalna poświata. */
data class Skin(val name: String, val base: Color, val shade: Color, val glow: Color?, val stripes: Boolean = false)

val Skins = listOf(
    Skin("Chrom", Color(0xFFE8E8E8), Color(0xFF3A3A3A), null),
    Skin("Perła", Color(0xFFFFF4E6), Color(0xFF9A8670), null),
    Skin("Neon", Color(0xFF00E5FF), Color(0xFF003A44), Color(0xFF00E5FF)),
    Skin("Lawa", Color(0xFFFF7A1A), Color(0xFF5A0F00), Color(0xFFFF3D00)),
    Skin("Toksyna", Color(0xFFB6FF3B), Color(0xFF1F3A00), Color(0xFF76FF03)),
    Skin("Bilard", Color(0xFFFFFFFF), Color(0xFF444444), null, stripes = true),
    Skin("Onyks", Color(0xFF3A3A3A), Color(0xFF050505), Color(0x66FFFFFF)),
    Skin("Róża", Color(0xFFFF8FC8), Color(0xFF55102F), Color(0xFFFF2D95)),
)

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color.Black,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.PanelHigh,
    outline = Palette.Line,
    error = Palette.Danger,
)

@Composable
fun MazeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
