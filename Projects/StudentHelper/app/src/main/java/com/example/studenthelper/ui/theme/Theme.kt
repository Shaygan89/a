package com.example.studenthelper.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF465EBD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE2FF),
    onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF585D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E2F9),
    onSecondaryContainer = Color(0xFF151A2C),
    tertiary = Color(0xFF75546F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F4),
    onTertiaryContainer = Color(0xFF2C122A),
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF191B23),
    surface = Color(0xFFF7F7FC),
    onSurface = Color(0xFF191B23),
    surfaceVariant = Color(0xFFE3E5F1),
    onSurfaceVariant = Color(0xFF454752),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C4FF),
    onPrimary = Color(0xFF102777),
    primaryContainer = Color(0xFF2D438F),
    onPrimaryContainer = Color(0xFFDEE2FF),
    secondary = Color(0xFFC3C6E0),
    secondaryContainer = Color(0xFF41465A),
    onSecondaryContainer = Color(0xFFE0E2F9),
    tertiary = Color(0xFFE4B9D9),
    tertiaryContainer = Color(0xFF5B3C56),
    onTertiaryContainer = Color(0xFFFFD7F4),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE3E1EA),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE3E1EA),
    surfaceVariant = Color(0xFF444653),
    onSurfaceVariant = Color(0xFFC5C6D3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun StudentHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    if (!view.isInEditMode) {
        // System-bar styling is static for the current theme. DisposableEffect
        // avoids repeating Window/insets-controller calls on ordinary recompositions.
        DisposableEffect(view, darkTheme, colors.background) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
