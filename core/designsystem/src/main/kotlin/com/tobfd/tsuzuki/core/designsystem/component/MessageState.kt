package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.tobfd.tsuzuki.core.designsystem.R
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing

/**
 * Centered empty state: icon tile, headline, text and an optional tonal action (e.g. "Browse").
 */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(TsuzukiSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(TsuzukiSizes.stateIconTile),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painter = icon, contentDescription = null)
            }
        }
        Spacer(Modifier.height(TsuzukiSpacing.large))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(TsuzukiSpacing.small))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(TsuzukiSpacing.extraLarge))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** Error state with a "Retry" action; every screen shows this when loading fails. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(TsuzukiIcons.CloudOff)
) {
    EmptyState(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier,
        actionLabel = stringResource(R.string.designsystem_retry),
        onAction = onRetry
    )
}

@ThemePreviews
@Composable
private fun EmptyStatePreview() {
    TsuzukiPreview {
        EmptyState(
            icon = painterResource(TsuzukiIcons.Inbox),
            title = "Nothing here yet",
            message = "Entries you add to this list show up here.",
            actionLabel = "Browse",
            onAction = {}
        )
    }
}

@ThemePreviews
@Composable
private fun ErrorStatePreview() {
    TsuzukiPreview {
        ErrorState(
            title = "You're offline",
            message = "Check your connection and try again.",
            onRetry = {}
        )
    }
}
