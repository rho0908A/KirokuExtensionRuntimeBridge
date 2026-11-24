package io.kiroku.runtime.host.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFE85D9E)
val AccentDark = Color(0xFF7A2B52)
val SurfaceDark = Color(0xFF121016)
val CardDark = Color(0xFF1D1A22)
val CardDarkHigh = Color(0xFF26222C)
val TextPrimary = Color(0xFFF4F1F5)
val TextSecondary = Color(0xFF9E97A6)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF2A0A19),
    secondary = Accent,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = CardDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDarkHigh,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3540),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB0326F),
    secondary = AccentDark,
)

@Composable
fun KirokuRuntimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
