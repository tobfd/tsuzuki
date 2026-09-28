package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.StatusColor
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme

/**
 * Filter chip for a list status. Selected: status container with onContainer text. Unselected:
 * outlined with an 8 dp dot in the status color.
 */
@Composable
fun StatusChip(
    label: String,
    statusColor: StatusColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon = if (selected) {
            null
        } else {
            { StatusDot(statusColor) }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = statusColor.container,
            selectedLabelColor = statusColor.onContainer,
            selectedLeadingIconColor = statusColor.onContainer
        )
    )
}

/** 8 dp dot in the status color. */
@Composable
fun StatusDot(statusColor: StatusColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(TsuzukiSizes.statusDot)
            .background(statusColor.color, CircleShape)
    )
}

@ThemePreviews
@Composable
private fun StatusChipPreview() {
    TsuzukiPreview {
        val colors = TsuzukiTheme.statusColors
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
            StatusChip(label = "Watching", statusColor = colors.current, selected = true, onClick = {})
            StatusChip(label = "Planning", statusColor = colors.planning, selected = false, onClick = {})
            StatusChip(label = "Completed", statusColor = colors.completed, selected = true, onClick = {})
            StatusChip(label = "Paused", statusColor = colors.paused, selected = false, onClick = {})
            StatusChip(label = "Dropped", statusColor = colors.dropped, selected = true, onClick = {})
            StatusChip(label = "Rewatching", statusColor = colors.repeating, selected = false, onClick = {})
        }
    }
}
