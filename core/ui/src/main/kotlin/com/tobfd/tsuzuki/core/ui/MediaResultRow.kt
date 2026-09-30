package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.tobfd.tsuzuki.core.designsystem.component.StatusDot
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaLite

/**
 * A search result (docs/DESIGN.md, Browse): a `MediaListRow` without +1, showing "TV · 2023 · 91%"
 * and, when the media is on the viewer's list, its status. Tap opens the detail page.
 */
@Composable
fun MediaResultRow(media: MediaLite, listStatus: MediaListStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .clickable(onClickLabel = stringResource(R.string.ui_open_details), onClick = onClick)
                .padding(TsuzukiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
        ) {
            MediaCover(
                imageUrl = media.coverUrl,
                contentDescription = null,
                placeholderColor = coverColorOrNull(media.coverColor),
                modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
            ) {
                Text(
                    text = media.title.userPreferred,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    mediaMeta(media),
                    media.averageScore?.let { stringResource(R.string.ui_average_score, it) }
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                listStatus?.let { status ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
                    ) {
                        StatusDot(status.statusColor())
                        Text(
                            text = stringResource(status.labelRes(media.type)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun MediaResultRowPreview() {
    TsuzukiPreview {
        Column(
            modifier = Modifier.padding(TsuzukiSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            MediaResultRow(PreviewListEntries.frieren.media, MediaListStatus.CURRENT, onClick = {})
            MediaResultRow(PreviewListEntries.dandadan.media, listStatus = null, onClick = {})
        }
    }
}
