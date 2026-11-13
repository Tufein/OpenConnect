package nl.tufein.openconnect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Arcade night": deep indigo screens, mint as the main colour and one colour per body signal, with monospace numbers
 * as a nod to old LCD scoreboards. The light scheme keeps the same colours on a pale indigo-white background.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF37F5A5),
    onPrimary = Color(0xFF00251A),
    primaryContainer = Color(0xFF0F3B33),
    onPrimaryContainer = Color(0xFFB8FFE0),
    secondary = Color(0xFFFFB547),
    onSecondary = Color(0xFF3A2400),
    secondaryContainer = Color(0xFF3F2F10),
    onSecondaryContainer = Color(0xFFFFDFA8),
    tertiary = Color(0xFF4FD3FF),
    onTertiary = Color(0xFF00303F),
    tertiaryContainer = Color(0xFF0F3447),
    onTertiaryContainer = Color(0xFFBFEBFF),
    background = Color(0xFF0B0E1A),
    onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF121729),
    onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Color(0xFF1D2540),
    onSurfaceVariant = Color(0xFF9AA6D0),
    surfaceContainerLowest = Color(0xFF0B0E1A),
    surfaceContainerLow = Color(0xFF111628),
    surfaceContainer = Color(0xFF161C32),
    surfaceContainerHigh = Color(0xFF1D2540),
    surfaceContainerHighest = Color(0xFF252E4F),
    outline = Color(0xFF3A4570),
    outlineVariant = Color(0xFF262F52),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3B0008),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00855A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F7E3),
    onPrimaryContainer = Color(0xFF00281B),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B8),
    onSecondaryContainer = Color(0xFF2E1A00),
    tertiary = Color(0xFF006C8E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6EBFF),
    onTertiaryContainer = Color(0xFF001F2C),
    background = Color(0xFFF4F6FD),
    onBackground = Color(0xFF141A33),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141A33),
    surfaceVariant = Color(0xFFE3E8F8),
    onSurfaceVariant = Color(0xFF4A5578),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FE),
    surfaceContainer = Color(0xFFECEFFA),
    surfaceContainerHigh = Color(0xFFE3E8F8),
    surfaceContainerHighest = Color(0xFFDBE1F5),
    outline = Color(0xFF7480A8),
    outlineVariant = Color(0xFFD5DAEE),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** One colour per signal, used for its ring, tile, chart and label so a number is recognised by colour alone. */
@Immutable
class MetricColors(
    val steps: Color,
    val heart: Color,
    val stress: Color,
    val battery: Color,
    val sleep: Color,
    val distance: Color,
    val calories: Color,
    val readiness: Color,
)

private val DarkMetrics = MetricColors(
    steps = Color(0xFF37F5A5),
    heart = Color(0xFFFF5C7A),
    stress = Color(0xFFFFA24A),
    battery = Color(0xFF4DA8FF),
    sleep = Color(0xFFA58BFF),
    distance = Color(0xFF4FD3FF),
    calories = Color(0xFFFFC84A),
    readiness = Color(0xFF37F5A5),
)

private val LightMetrics = MetricColors(
    steps = Color(0xFF00855A),
    heart = Color(0xFFD63355),
    stress = Color(0xFFC26A00),
    battery = Color(0xFF1F6FD1),
    sleep = Color(0xFF6C4FE0),
    distance = Color(0xFF007FA6),
    calories = Color(0xFFA87400),
    readiness = Color(0xFF00855A),
)

val LocalMetricColors = staticCompositionLocalOf { DarkMetrics }

/** The colours of the signals in the current theme. */
object OpenConnectColors {
    val metrics: MetricColors
        @Composable get() = LocalMetricColors.current
}

private val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    displayMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** [mode] is "system" (follow the phone), "dark" or "light". */
@Composable
fun OpenConnectTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalMetricColors provides if (dark) DarkMetrics else LightMetrics) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
