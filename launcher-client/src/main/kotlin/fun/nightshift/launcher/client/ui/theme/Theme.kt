package `fun`.nightshift.launcher.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NightShift launcher theme — dark purple palette matching the in-game ClickGUI
 * (Requirement 19.1). Material3 dark scheme is used as the base; brand colours
 * override surfaces, primary, and secondary tones.
 */
object NsColors {
    /** Top of the gradient backdrop — near-black with a violet tint. */
    val BackgroundTop: Color = Color(0xFF0E0A18)
    /** Bottom of the gradient backdrop. */
    val BackgroundBottom: Color = Color(0xFF1A0E2E)

    /** Surface for cards and dialogs. */
    val Surface: Color = Color(0xFF1B1430)

    /** Brand purple, used on primary actions and highlights. */
    val Accent: Color = Color(0xFFA869FF)
    val AccentMuted: Color = Color(0xFF50328C)

    /** Hi-emphasis text. */
    val TextPrimary: Color = Color(0xFFEBEBF5)
    val TextSecondary: Color = Color(0xFF9A93B8)
    val TextDisabled: Color = Color(0x66EBEBF5)

    /** Subtle separator lines / outlines. */
    val Outline: Color = Color(0x4650328C)

    val Error: Color = Color(0xFFFF554B)
    val Success: Color = Color(0xFF6EE7B7)
}

/** Minimal Material3 typography reusing the system sans-serif until Manrope is bundled. */
private val NsTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = NsColors.TextPrimary),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = NsColors.TextPrimary),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = NsColors.TextPrimary),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = NsColors.TextSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NsColors.TextPrimary),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = NsColors.TextSecondary),
)

private val NsShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun NightShiftTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = NsColors.Accent,
        onPrimary = Color.White,
        secondary = NsColors.AccentMuted,
        onSecondary = NsColors.TextPrimary,
        background = NsColors.BackgroundBottom,
        onBackground = NsColors.TextPrimary,
        surface = NsColors.Surface,
        onSurface = NsColors.TextPrimary,
        surfaceVariant = NsColors.Surface,
        onSurfaceVariant = NsColors.TextSecondary,
        outline = NsColors.Outline,
        error = NsColors.Error,
        onError = Color.White,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = NsTypography,
        shapes = NsShapes,
        content = content,
    )
}
