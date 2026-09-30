package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.colorScheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A selectable card for a color source (docs/DESIGN.md, Settings): the name, a row of swatches and a check
 * when selected. Two of them side by side act as a radio group.
 */
@Composable
fun ColorChoiceCard(
    label: String,
    swatches: ImmutableList<Color>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TsuzukiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        painter = painterResource(TsuzukiIcons.Check),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(TsuzukiSizes.colorSwatch)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
                swatches.forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .size(TsuzukiSizes.colorSwatch)
                            .background(swatch, CircleShape)
                    )
                }
            }
        }
    }
}

/** The swatches that stand for [colorSource]: primary, secondary, tertiary and the primary container. */
@Composable
fun colorSourceSwatches(colorSource: ColorSource, darkTheme: Boolean): ImmutableList<Color> {
    val scheme = colorScheme(colorSource, darkTheme)
    return persistentListOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
}

@ThemePreviews
@Composable
private fun ColorChoiceCardPreview() {
    TsuzukiPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
            ColorChoiceCard(
                label = "Material You",
                swatches = colorSourceSwatches(ColorSource.Dynamic, darkTheme = false),
                selected = true,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
            ColorChoiceCard(
                label = "AniList blue",
                swatches = colorSourceSwatches(ColorSource.AniListBlue, darkTheme = false),
                selected = false,
                onClick = {},
                modifier = Modifier.weight(1f)
            )
        }
    }
}
