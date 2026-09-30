package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Large screens (the Tablet package in docs/ROADMAP.md). Layouts that follow the space they get use
 * adaptive grids or `BoxWithConstraints`, so a list pane next to the detail pane stays narrow; only
 * decisions about the whole window (a sheet shown as a dialog) use this.
 */
@Composable
fun isExpandedWindow(): Boolean = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(
    WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
)

/**
 * At most [maxWidth] wide and centred in the space it gets; on phones it changes nothing. For running
 * text ([TsuzukiSizes.readingWidth]) and forms ([TsuzukiSizes.formWidth]).
 */
fun Modifier.centeredMaxWidth(maxWidth: Dp = TsuzukiSizes.readingWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth)

/**
 * Lets a full-width item of a grid with side padding ([start], [end]) reach under that padding, so a
 * horizontal row inside it scrolls from edge to edge like it does in a plain list.
 */
fun Modifier.bleedHorizontally(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val left = start.roundToPx()
    val extra = left + end.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-left, 0) }
}
