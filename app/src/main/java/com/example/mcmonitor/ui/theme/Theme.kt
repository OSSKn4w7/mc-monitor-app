package com.example.mcmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.mcmonitor.data.ThemeMode

// 以"草方块绿"为基调的自定义配色
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF0B3D0E),
    secondary = Color(0xFF558B2F),
    onSecondary = Color.White,
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF00330A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFAED581),
    onSecondary = Color(0xFF1B3300),
    background = Color(0xFF121512),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF121512),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun McMonitorTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.material3.MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
