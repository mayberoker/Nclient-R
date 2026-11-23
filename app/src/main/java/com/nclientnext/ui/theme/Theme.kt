package com.nclientnext.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nclientnext.data.prefs.AppSettings
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Nclient-R palette: pitch black, white glass, red accent. */
object RColors {
    val Black = Color(0xFF000000)
    val Red = Color(0xFFFF2B3F)
    val RedDeep = Color(0xFFB0001A)
    val RedSoft = Color(0xFFFF8A96)
    /** Solid panel colour for anything that pops up over content (menus, dialogs, sheets). */
    val Panel = Color(0xFF161618)
    val PanelHigh = Color(0xFF1C1C1F)
}

private val Scheme = darkColorScheme(
    primary = RColors.Red,
    onPrimary = Color.White,
    primaryContainer = RColors.RedDeep,
    onPrimaryContainer = Color.White,
    inversePrimary = RColors.RedDeep,
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2A2D),
    onSecondaryContainer = Color.White,
    tertiary = RColors.RedSoft,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A1016),
    onTertiaryContainer = Color.White,
    error = RColors.Red,
    onError = Color.White,
    errorContainer = Color(0xFF4A0B14),
    onErrorContainer = Color(0xFFFFDADD),
    background = RColors.Black,
    onBackground = Color.White,
    // Glass: base surfaces are see-through; anything that floats is solid so text stays readable.
    surface = Color.Transparent,
    onSurface = Color.White,
    surfaceDim = Color.Transparent,
    surfaceBright = Color.White.copy(alpha = 0.08f),
    surfaceContainerLowest = Color.Transparent,
    surfaceContainerLow = RColors.Panel,
    surfaceContainer = RColors.Panel,
    surfaceContainerHigh = RColors.PanelHigh,
    surfaceContainerHighest = Color.White.copy(alpha = 0.08f),
    surfaceVariant = Color.White.copy(alpha = 0.08f),
    onSurfaceVariant = Color(0xFFB8B8BC),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color.Black,
    outline = Color(0xFF6E6E73),
    outlineVariant = Color.White.copy(alpha = 0.14f),
    scrim = Color.Black,
)

/** Colors for the frosted-glass look; read via [LocalGlass]. */
@Immutable
data class GlassColors(
    val dark: Boolean,
    /** Fill for glass panels (cards, chips, pills). */
    val tint: Color,
    /** Stronger fill for selected / pressed glass. */
    val tintStrong: Color,
    /** Specular rim: bright at the top-left edge, fading out. */
    val rim: Brush,
    /** Inner sheen drawn across the top of a panel. */
    val sheen: Brush,
    /** Style for real backdrop blur (bars, nav pill). Heavy enough that content behind never fights the text. */
    val barStyle: HazeStyle,
    val base: Color,
)

val LocalGlass = staticCompositionLocalOf<GlassColors> { error("No glass colors") }

val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val Glass = GlassColors(
    dark = true,
    tint = Color.White.copy(alpha = 0.075f),
    tintStrong = Color.White.copy(alpha = 0.16f),
    rim = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.18f)),
        start = Offset.Zero, end = Offset.Infinite,
    ),
    sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)),
    barStyle = HazeStyle(
        backgroundColor = RColors.Black,
        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.62f))),
        blurRadius = 40.dp,
        noiseFactor = 0.03f,
        // Android 11 and older can't blur: use an almost solid bar instead of a see-through one.
        fallbackTint = HazeTint(Color(0xF00A0A0B)),
    ),
    base = RColors.Black,
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun NClientTheme(settings: AppSettings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlass provides Glass) {
        MaterialTheme(colorScheme = Scheme, typography = Typography(), shapes = GlassShapes, content = content)
    }
}

/** Glass panel: translucent white fill, top sheen and a specular rim. */
@Composable
fun Modifier.glass(shape: Shape = MaterialTheme.shapes.medium, strong: Boolean = false, rimWidth: Float = 1f): Modifier {
    val g = LocalGlass.current
    return this
        .clip(shape)
        .background(if (strong) g.tintStrong else g.tint, shape)
        .background(g.sheen, shape)
        .border(rimWidth.dp, g.rim, shape)
}

/** Just the specular rim, for things that already have content (cover images). */
@Composable
fun Modifier.glassRim(shape: Shape = MaterialTheme.shapes.medium): Modifier =
    this.border(1.dp, LocalGlass.current.rim, shape)

/**
 * Pitch-black backdrop with a faint red glow in the top corner so the glass has something to catch.
 * Static on purpose: the glass bars blur this, and a moving background would re-blur every frame.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(RColors.Black)
            .drawBehind {
                val w = size.width
                drawCircle(
                    Brush.radialGradient(listOf(RColors.Red.copy(alpha = 0.10f), Color.Transparent), center = Offset(w * 0.1f, 0f), radius = w * 0.9f),
                    radius = w * 0.9f, center = Offset(w * 0.1f, 0f),
                )
            },
    ) { content() }
}
