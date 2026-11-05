package com.example.simplelauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6B4E),
    onPrimary = Color.White,
    background = Color(0xFFF5F5F2),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3AE),
    onPrimary = Color.Black,
    background = Color(0xFF121416),
    surface = Color(0xFF1C1F22),
)

@Composable
fun simplelauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        // Intentionally no custom typography/shape overrides — keep it plain & functional.
        content = content,
    )
}
