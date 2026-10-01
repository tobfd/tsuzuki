package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.colorScheme
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings

/**
 * The widgets' colors, following the app's appearance settings (docs/DESIGN.md, Widgets): [light]
 * and [dark] are the schemes the widget shows in the system's light and dark mode. With the app on
 * Material You and the system mode, [followsWallpaper] is set and the widgets use Glance's dynamic
 * colors, which the launcher updates by itself when the wallpaper changes.
 */
internal data class WidgetPalette(val light: ColorScheme, val dark: ColorScheme, val followsWallpaper: Boolean)

internal fun widgetPalette(context: Context, appearance: AppearanceSettings): WidgetPalette {
    val source = when (appearance.colors) {
        AppColors.MaterialYou -> ColorSource.Dynamic
        AppColors.AniListBlue -> ColorSource.AniListBlue
    }

    fun scheme(dark: Boolean) = colorScheme(context, source, dark, appearance.pureBlack)
    return when (appearance.themeMode) {
        AppThemeMode.System -> WidgetPalette(
            light = scheme(dark = false),
            dark = scheme(dark = true),
            followsWallpaper = source == ColorSource.Dynamic && !appearance.pureBlack
        )

        AppThemeMode.Light -> scheme(dark = false).let { WidgetPalette(it, it, followsWallpaper = false) }

        AppThemeMode.Dark -> scheme(dark = true).let { WidgetPalette(it, it, followsWallpaper = false) }
    }
}

@Composable
internal fun WidgetTheme(palette: WidgetPalette, content: @Composable () -> Unit) {
    if (palette.followsWallpaper) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = palette.toColorProviders(), content = content)
    }
}

/**
 * Glance's own mapping (`glance-material3`) puts the widget on `secondaryContainer`; the widgets
 * here sit on `surfaceContainer` like the app's cards, which also keeps pure black close to black.
 */
private fun WidgetPalette.toColorProviders(): ColorProviders {
    fun role(pick: ColorScheme.() -> Color) = ColorProvider(day = light.pick(), night = dark.pick())
    return colorProviders(
        primary = role { primary },
        onPrimary = role { onPrimary },
        primaryContainer = role { primaryContainer },
        onPrimaryContainer = role { onPrimaryContainer },
        secondary = role { secondary },
        onSecondary = role { onSecondary },
        secondaryContainer = role { secondaryContainer },
        onSecondaryContainer = role { onSecondaryContainer },
        tertiary = role { tertiary },
        onTertiary = role { onTertiary },
        tertiaryContainer = role { tertiaryContainer },
        onTertiaryContainer = role { onTertiaryContainer },
        error = role { error },
        errorContainer = role { errorContainer },
        onError = role { onError },
        onErrorContainer = role { onErrorContainer },
        background = role { background },
        onBackground = role { onBackground },
        surface = role { surface },
        onSurface = role { onSurface },
        surfaceVariant = role { surfaceVariant },
        onSurfaceVariant = role { onSurfaceVariant },
        outline = role { outline },
        inverseOnSurface = role { inverseOnSurface },
        inverseSurface = role { inverseSurface },
        inversePrimary = role { inversePrimary },
        widgetBackground = role { surfaceContainer }
    )
}
