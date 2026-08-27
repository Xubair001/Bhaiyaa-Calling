package com.codeaza.bhaiyaaa.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.codeaza.bhaiyaaa.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = GreenOnPrimaryLight,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = GreenOnContainerLight,
    secondary = BrassSecondaryLight,
    onSecondary = BrassOnSecondaryLight,
    secondaryContainer = BrassContainerLight,
    onSecondaryContainer = BrassOnContainerLight,
    tertiary = GoldTertiaryLight,
    onTertiary = GoldOnTertiaryLight,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = GoldOnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = GreenOnPrimaryDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenOnContainerDark,
    secondary = BrassSecondaryDark,
    onSecondary = BrassOnSecondaryDark,
    secondaryContainer = BrassContainerDark,
    onSecondaryContainer = BrassOnContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = GoldOnTertiaryDark,
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = GoldOnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

/**
 * @param themeMode the user's explicit choice; SYSTEM defers to the OS setting.
 * @param dynamicColor use Android 12+ wallpaper colours when available. Off by
 *   default on older devices, where the API simply doesn't exist.
 */
@Composable
fun BhaiyaaaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Drive the status/navigation bar icon contrast from the in-app
            // theme, so choosing Dark in Settings while the system is Light
            // still gets readable system-bar icons.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BhaiyaaaTypography,
        content = content
    )
}
