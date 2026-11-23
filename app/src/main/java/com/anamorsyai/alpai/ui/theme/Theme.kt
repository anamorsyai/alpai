package com.anamorsyai.alpai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.anamorsyai.alpai.data.repository.ThemeMode

// Aurora-inspired palette, matching the AlpDroid "Aurora Glass" look:
// violet primary, teal -> violet gradient accent.
val AuroraViolet = Color(0xFF8B7CF6)
val AuroraTeal = Color(0xFF2DD4BF)
val AuroraDeep = Color(0xFF12101F)

private val LightColors = lightColorScheme(
    primary = AuroraViolet,
    secondary = AuroraTeal,
    tertiary = AuroraViolet,
    background = Color(0xFFF7F6FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEAF7),
    onPrimary = Color.White,
    onBackground = Color(0xFF191828),
    onSurface = Color(0xFF191828),
)

private val DarkColors = darkColorScheme(
    primary = AuroraViolet,
    secondary = AuroraTeal,
    tertiary = AuroraViolet,
    background = Color(0xFF0D0C16),
    surface = Color(0xFF171524),
    surfaceVariant = Color(0xFF221F33),
    onPrimary = Color.White,
    onBackground = Color(0xFFF2F0FA),
    onSurface = Color(0xFFF2F0FA),
)

@Composable
fun AlpAiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
