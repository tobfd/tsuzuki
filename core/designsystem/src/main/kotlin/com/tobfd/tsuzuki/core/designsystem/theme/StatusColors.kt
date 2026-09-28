package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Color set for one list status: [color] for dots and text, [container]/[onContainer] for chips. */
@Immutable
data class StatusColor(val color: Color, val container: Color, val onContainer: Color)

/**
 * Colors for the six list statuses, from design/tokens.json (color.status). They are fixed custom
 * colors harmonized to #3DB4F2 and stay the same for dynamic color and AniList blue.
 */
@Immutable
data class StatusColors(
    val current: StatusColor,
    val planning: StatusColor,
    val completed: StatusColor,
    val paused: StatusColor,
    val dropped: StatusColor,
    val repeating: StatusColor
)

internal val LightStatusColors = StatusColors(
    current = StatusColor(Color(0xFF00658E), Color(0xFFC7E7FF), Color(0xFF001E2E)),
    planning = StatusColor(Color(0xFF9B432C), Color(0xFFFFDBD1), Color(0xFF3C0800)),
    completed = StatusColor(Color(0xFF006D36), Color(0xFFB8F5C8), Color(0xFF00210C)),
    paused = StatusColor(Color(0xFF6B5F00), Color(0xFFF9E466), Color(0xFF201C00)),
    dropped = StatusColor(Color(0xFFA82E6A), Color(0xFFFFD9E4), Color(0xFF3E0021)),
    repeating = StatusColor(Color(0xFF4F51BC), Color(0xFFE1E0FF), Color(0xFF07006C))
)

internal val DarkStatusColors = StatusColors(
    current = StatusColor(Color(0xFF84CFFF), Color(0xFF004C6C), Color(0xFFC7E7FF)),
    planning = StatusColor(Color(0xFFFFB4A1), Color(0xFF7C2D17), Color(0xFFFFDBD1)),
    completed = StatusColor(Color(0xFF44E181), Color(0xFF005227), Color(0xFFB8F5C8)),
    paused = StatusColor(Color(0xFFDBC74D), Color(0xFF514700), Color(0xFFF9E466)),
    dropped = StatusColor(Color(0xFFFFB0CD), Color(0xFF881151), Color(0xFFFFD9E4)),
    repeating = StatusColor(Color(0xFFC0C1FF), Color(0xFF3638A3), Color(0xFFE1E0FF))
)

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
