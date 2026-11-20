package com.pdfimage.cropper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pdfimage.cropper.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF000000), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6), onPrimaryContainer = Color.Black,
    secondary = Color(0xFF3A3A3A), onSecondary = Color.White,
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF111111),
    surface = Color.White, onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFBDBDBD), error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A2A2A), onPrimaryContainer = Color.White,
    secondary = Color(0xFFCCCCCC), onSecondary = Color.Black,
    background = Color(0xFF0B0B0B), onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF151515), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF252525), onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF5A5A5A), error = Color(0xFFF2B8B5)
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
