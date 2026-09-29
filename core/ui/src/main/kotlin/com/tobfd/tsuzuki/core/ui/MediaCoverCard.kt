package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaLite

/** Width of cover cards in horizontal rows (Trending, relations, recommendations). */
val CoverCardWidth: Dp = 120.dp

/**
 * A cover with title and a meta line, for horizontal rows (Trending now, relations,
 * recommendations). [label] goes above the title, e.g. the relation type.
 */
@Composable
fun MediaCoverCard(
    media: MediaLite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    meta: String? = mediaMeta(media)
) {
    Column(
        modifier = modifier
            .width(CoverCardWidth)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
    ) {
        MediaCover(
            imageUrl = media.coverUrl,
            contentDescription = null,
            placeholderColor = coverColorOrNull(media.coverColor),
            modifier = Modifier.fillMaxWidth()
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = media.title.userPreferred,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "TV · 2023", or null when neither is known. */
@Composable
fun mediaMeta(media: MediaLite): String? =
    listOfNotNull(media.format?.let { stringResource(it.labelRes()) }, media.year?.toString())
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
