package com.tobfd.tsuzuki.core.ui

import androidx.compose.ui.graphics.Color

private const val RGB_HEX_LENGTH = 6
private const val OPAQUE = 0xFF000000

/**
 * Parses AniList's `coverImage.color` ("#bbf1a1") for use as a cover placeholder. Returns null for
 * a missing or malformed value, so callers fall back to the theme's surfaceContainerHigh.
 */
fun coverColorOrNull(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != RGB_HEX_LENGTH) return null
    val rgb = digits.toLongOrNull(radix = 16) ?: return null
    return Color(OPAQUE or rgb)
}
