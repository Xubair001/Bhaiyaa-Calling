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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.codeaza.bhaiyaaa.domain.model.ThemeMode
import com.codeaza.bhaiyaaa.domain.model.VipLevel

private val LightColors = lightColorScheme(
    primary = SaffronPrimaryLight,
    onPrimary = SaffronOnPrimaryLight,
    primaryContainer = SaffronContainerLight,
    onPrimaryContainer = SaffronOnContainerLight,
    secondary = TerracottaSecondaryLight,
    onSecondary = TerracottaOnSecondaryLight,
    secondaryContainer = TerracottaContainerLight,
    onSecondaryContainer = TerracottaOnContainerLight,
    tertiary = PlumTertiaryLight,
    onTertiary = PlumOnTertiaryLight,
    tertiaryContainer = PlumContainerLight,
    onTertiaryContainer = PlumOnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineLight,
    outlineVariant = SurfaceVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColors = darkColorScheme(
    primary = SaffronPrimaryDark,
    onPrimary = SaffronOnPrimaryDark,
    primaryContainer = SaffronContainerDark,
    onPrimaryContainer = SaffronOnContainerDark,
    secondary = TerracottaSecondaryDark,
    onSecondary = TerracottaOnSecondaryDark,
    secondaryContainer = TerracottaContainerDark,
    onSecondaryContainer = TerracottaOnContainerDark,
    tertiary = PlumTertiaryDark,
    onTertiary = PlumOnTertiaryDark,
    tertiaryContainer = PlumContainerDark,
    onTertiaryContainer = PlumOnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = OutlineDark,
    outlineVariant = SurfaceVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

/**
 * @param themeMode the user's explicit choice; SYSTEM defers to the OS.
 * @param dynamicColor use Android 12+ wallpaper colours. When on, the wallpaper
 *   wins over the palette above - that is the user's call to make, and the
 *   setting says as much.
 */
@Composable
fun BhaiyaaaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
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
            // Driven from the in-app theme, not the system one, so choosing Dark
            // while the phone is in Light still gets readable status-bar icons.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BhaiyaaaTypography,
        shapes = BhaiyaaaShapes,
        content = content
    )
}

/**
 * The colour for a VIP tier, taken from the theme rather than hard-coded.
 *
 * Plum -> saffron -> vermilion is an escalation the eye reads as ranked, and
 * because each is a semantic role it adapts to light, dark and wallpaper
 * theming instead of staying a fixed value that eventually clashes.
 */
@Composable
fun accentFor(level: VipLevel): Color = when (level) {
    VipLevel.EMERGENCY -> MaterialTheme.colorScheme.error
    VipLevel.SUPER_VIP -> MaterialTheme.colorScheme.primary
    VipLevel.VIP -> MaterialTheme.colorScheme.tertiary
    VipLevel.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}
