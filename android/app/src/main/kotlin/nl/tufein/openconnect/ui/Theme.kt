package nl.tufein.openconnect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Phosphor green on near-black, like an old CRT. A calmer light scheme follows the system setting. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CFF4A),
    onPrimary = Color(0xFF032000),
    primaryContainer = Color(0xFF14330F),
    onPrimaryContainer = Color(0xFFB5FFAA),
    secondary = Color(0xFFFFB000),
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF3A2D08),
    onSecondaryContainer = Color(0xFFFFDF9C),
    tertiary = Color(0xFF58D8FF),
    background = Color(0xFF0A0F0A),
    onBackground = Color(0xFFCFF5C8),
    surface = Color(0xFF0F160F),
    onSurface = Color(0xFFCFF5C8),
    surfaceVariant = Color(0xFF182418),
    onSurfaceVariant = Color(0xFF9FC59A),
    surfaceContainer = Color(0xFF111A11),
    surfaceContainerHigh = Color(0xFF182418),
    outline = Color(0xFF3B5A38),
    outlineVariant = Color(0xFF223520),
    error = Color(0xFFFF6B5E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF17700F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F5CD),
    onPrimaryContainer = Color(0xFF062B02),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4B0),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF00677F),
    background = Color(0xFFF4F8F2),
    onBackground = Color(0xFF111A10),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111A10),
    surfaceVariant = Color(0xFFE3EDE0),
    onSurfaceVariant = Color(0xFF3B4B39),
    surfaceContainer = Color(0xFFEAF2E7),
    surfaceContainerHigh = Color(0xFFE3EDE0),
    outline = Color(0xFF6F8B6C),
    outlineVariant = Color(0xFFC6D6C3),
    error = Color(0xFFB3261E),
)

private val Mono = FontFamily.Monospace

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun OpenConnectTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}
