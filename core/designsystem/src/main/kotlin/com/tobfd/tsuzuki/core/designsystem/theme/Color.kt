package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// AniList blue: SchemeFidelity from seed #3DB4F2, values from design/tokens.json (color.anilistBlue).
// Roles the tokens leave out follow material-color-utilities: background = surface,
// surfaceVariant = surfaceContainerHighest, surfaceTint = primary.

private val LightSurface = Color(0xFFF6FAFF)
private val LightOnSurface = Color(0xFF171C20)
private val LightSurfaceContainerHighest = Color(0xFFDFE3E8)
private val LightPrimary = Color(0xFF00658E)

internal val AniListBlueLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3DB4F2),
    onPrimaryContainer = Color(0xFF004360),
    secondary = Color(0xFF3E627A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBCE2FE),
    onSecondaryContainer = Color(0xFF41657D),
    tertiary = Color(0xFF845400),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE69A24),
    onTertiaryContainer = Color(0xFF593700),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHighest,
    onSurfaceVariant = Color(0xFF3E4850),
    surfaceTint = LightPrimary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F9),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E8EE),
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = Color(0xFF6E7881),
    outlineVariant = Color(0xFFBEC8D1),
    inverseSurface = Color(0xFF2C3135),
    inverseOnSurface = Color(0xFFEDF1F6),
    inversePrimary = Color(0xFF84CFFF),
    surfaceDim = Color(0xFFD6DAE0),
    surfaceBright = LightSurface
)

private val DarkSurface = Color(0xFF0F1418)
private val DarkOnSurface = Color(0xFFDFE3E8)
private val DarkSurfaceContainerHighest = Color(0xFF303539)
private val DarkPrimary = Color(0xFF84CFFF)

internal val AniListBlueDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF3DB4F2),
    onPrimaryContainer = Color(0xFF004360),
    secondary = Color(0xFFA6CBE6),
    onSecondary = Color(0xFF08344A),
    secondaryContainer = Color(0xFF284D64),
    onSecondaryContainer = Color(0xFF98BDD8),
    tertiary = Color(0xFFFFB95A),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFFE69A24),
    onTertiaryContainer = Color(0xFF593700),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHighest,
    onSurfaceVariant = Color(0xFFBEC8D1),
    surfaceTint = DarkPrimary,
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF262B2F),
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = Color(0xFF88929B),
    outlineVariant = Color(0xFF3E4850),
    inverseSurface = DarkOnSurface,
    inverseOnSurface = Color(0xFF2C3135),
    inversePrimary = LightPrimary,
    surfaceDim = DarkSurface,
    surfaceBright = Color(0xFF353A3E)
)
