package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from design/tokens.json (shape). "full" is a pill or circle (`CircleShape`). */
internal object ShapeTokens {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 28.dp
}

/**
 * Usage: progress bar extraSmall, chips small, covers medium, cards and list rows large, bottom
 * sheets extraLarge (top corners), buttons full.
 */
internal val TsuzukiShapes = Shapes(
    extraSmall = RoundedCornerShape(ShapeTokens.extraSmall),
    small = RoundedCornerShape(ShapeTokens.small),
    medium = RoundedCornerShape(ShapeTokens.medium),
    large = RoundedCornerShape(ShapeTokens.large),
    extraLarge = RoundedCornerShape(ShapeTokens.extraLarge)
)
