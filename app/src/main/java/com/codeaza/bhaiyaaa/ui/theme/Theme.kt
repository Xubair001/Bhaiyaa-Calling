package com.codeaza.bhaiyaaa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BhaiGreen,
    secondary = BhaiGold,
    background = BhaiBackgroundLight,
    surface = BhaiSurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = BhaiGreenLight,
    secondary = BhaiGold,
    background = BhaiBackgroundDark,
    surface = BhaiSurfaceDark
)

@Composable
fun BhaiyaaaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BhaiyaaaTypography,
        content = content
    )
}
