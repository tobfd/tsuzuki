package com.tobfd.tsuzuki.core.model

/** Where the app's colors come from (docs/PRODUCT.md, D3). */
enum class AppColors {
    /** Material You: colors from the wallpaper. The default. */
    MaterialYou,

    /** The fixed AniList blue schemes from design/tokens.json. */
    AniListBlue
}

enum class AppThemeMode {
    System,
    Light,
    Dark
}

/** The app's own look, stored on the device (not on AniList) and kept across logins. */
data class AppearanceSettings(
    val colors: AppColors = AppColors.MaterialYou,
    val themeMode: AppThemeMode = AppThemeMode.System,
    /** "Pure black": a black background in the dark theme (AMOLED). */
    val pureBlack: Boolean = false
)
