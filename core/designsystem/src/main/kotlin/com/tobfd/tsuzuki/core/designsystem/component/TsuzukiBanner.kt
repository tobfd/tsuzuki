package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing

/**
 * An inline notice with an optional action, e.g. "Your login expires in 5 days · Log in again" or
 * "AniList is busy". secondaryContainer, large shape; TalkBack announces it when it appears.
 */
@Composable
fun TsuzukiBanner(
    message: String,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(TsuzukiIcons.Info),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(
                start = TsuzukiSpacing.large,
                end = TsuzukiSpacing.small,
                top = TsuzukiSpacing.medium,
                bottom = if (actionLabel == null) TsuzukiSpacing.medium else TsuzukiSpacing.extraSmall
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painter = icon, contentDescription = null)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun TsuzukiBannerPreview() {
    TsuzukiPreview {
        Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
            TsuzukiBanner(
                message = "Your AniList login expires in 5 days. Log in again to renew it.",
                actionLabel = "Log in again",
                onAction = {}
            )
            TsuzukiBanner(message = "AniList is busy. Retrying in 30 seconds.")
        }
    }
}
