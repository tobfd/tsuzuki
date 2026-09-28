package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/** Where the color scheme comes from (docs/PRODUCT.md, D3). */
enum class ColorSource {
    /** Material You: colors from the wallpaper. The default. */
    Dynamic,

    /** The fixed AniList blue schemes from design/tokens.json. */
    AniListBlue
}

enum class ThemeMode {
    System,
    Light,
    Dark
}

/**
 * The app theme: color scheme from [colorSource], light or dark from [themeMode], the Tsuzuki type
 * scale and shapes, and [TsuzukiTheme.statusColors].
 *
 * Motion uses Material's standard scheme: the M3 Expressive motion scheme (`MotionScheme`,
 * `MaterialExpressiveTheme`) is internal in material3 1.4.0 and only public in the 1.5 alphas.
 */
@Composable
fun TsuzukiTheme(
    colorSource: ColorSource = ColorSource.Dynamic,
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = when (colorSource) {
        // minSdk 31: dynamic color exists on every supported device.
        ColorSource.Dynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        ColorSource.AniListBlue -> if (darkTheme) AniListBlueDarkColorScheme else AniListBlueLightColorScheme
    }
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TsuzukiTypography,
            shapes = TsuzukiShapes,
            content = content
        )
    }
}

/** Theme values beyond `MaterialTheme`. */
object TsuzukiTheme {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
