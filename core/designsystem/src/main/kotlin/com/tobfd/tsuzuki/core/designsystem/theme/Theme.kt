package com.tobfd.tsuzuki.core.designsystem.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

/** Share of each surface container's own color kept in pure black (design/tokens.json, color.pureBlack). */
private const val PURE_BLACK_CONTAINER_MIX = 0.6f

/**
 * The app theme: color scheme from [colorSource], light or dark from [themeMode], the Tsuzuki type
 * scale and shapes, and [TsuzukiTheme.statusColors]. [pureBlack] makes the dark theme's background black
 * (AMOLED); the light theme ignores it.
 *
 * Motion uses Material's standard scheme: the M3 Expressive motion scheme (`MotionScheme`,
 * `MaterialExpressiveTheme`) is internal in material3 1.4.0 and only public in the 1.5 alphas.
 */
@Composable
fun TsuzukiTheme(
    colorSource: ColorSource = ColorSource.Dynamic,
    themeMode: ThemeMode = ThemeMode.System,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val baseScheme = colorScheme(colorSource, darkTheme)
    val colorScheme = if (darkTheme && pureBlack) baseScheme.pureBlack() else baseScheme
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

/** The light or dark scheme of [colorSource]; the settings' color cards show both sources side by side. */
@Composable
@ReadOnlyComposable
fun colorScheme(colorSource: ColorSource, darkTheme: Boolean): ColorScheme = when (colorSource) {
    // minSdk 31: dynamic color exists on every supported device.
    ColorSource.Dynamic -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    ColorSource.AniListBlue -> if (darkTheme) AniListBlueDarkColorScheme else AniListBlueLightColorScheme
}

/**
 * The scheme [TsuzukiTheme] uses, outside of composition: the home-screen widgets theme themselves
 * with it.
 */
fun colorScheme(context: Context, colorSource: ColorSource, darkTheme: Boolean, pureBlack: Boolean): ColorScheme {
    val base = when (colorSource) {
        ColorSource.Dynamic -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        ColorSource.AniListBlue -> if (darkTheme) AniListBlueDarkColorScheme else AniListBlueLightColorScheme
    }
    return if (darkTheme && pureBlack) base.pureBlack() else base
}

/** Black background and surface; containers keep their order, raised slightly above the black. */
internal fun ColorScheme.pureBlack(): ColorScheme {
    fun Color.towardsBlack() = lerp(Color.Black, this, PURE_BLACK_CONTAINER_MIX)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLow.towardsBlack(),
        surfaceContainer = surfaceContainer.towardsBlack(),
        surfaceContainerHigh = surfaceContainerHigh.towardsBlack(),
        surfaceContainerHighest = surfaceContainerHighest.towardsBlack()
    )
}

/** Theme values beyond `MaterialTheme`. */
object TsuzukiTheme {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
