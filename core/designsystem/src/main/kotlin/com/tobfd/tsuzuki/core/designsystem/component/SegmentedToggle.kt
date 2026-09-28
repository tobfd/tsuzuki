package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Single-choice segmented button row, e.g. Anime / Manga or Following / Global. Pass
 * `Modifier.fillMaxWidth()` for the full-width variant.
 */
@Composable
fun SegmentedToggle(
    options: ImmutableList<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}

@ThemePreviews
@Composable
private fun SegmentedTogglePreview() {
    TsuzukiPreview {
        SegmentedToggle(
            options = persistentListOf("Anime", "Manga"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.fillMaxWidth()
        )
        SegmentedToggle(
            options = persistentListOf("Following", "Global"),
            selectedIndex = 1,
            onSelect = {}
        )
    }
}
