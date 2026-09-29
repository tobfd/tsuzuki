package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.tobfd.tsuzuki.core.designsystem.R as DesignR
import com.tobfd.tsuzuki.core.designsystem.component.PlusOneButton
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.ListEntryActions
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.fromRaw
import com.tobfd.tsuzuki.core.ui.score.ScoreText

/**
 * A row of the viewer's list (docs/DESIGN.md, `MediaListRow`): thumbnail, title, "TV · 2023 · 9.0",
 * progress, and +1 while watching or "Start" while planning. Tap edits the entry, long press opens
 * the detail page. TalkBack reads the row as one item with +1 / Start as actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListRow(
    entry: MediaListEntry,
    scoreFormat: ScoreFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlusOne: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val total = media.total
    val canPlusOne = ListEntryActions.canPlusOne(entry)
    val canStart = entry.status == MediaListStatus.PLANNING
    val plusOneLabel = stringResource(DesignR.string.designsystem_plus_one_description)
    val startLabel = stringResource(R.string.ui_list_start)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (canPlusOne) add(CustomAccessibilityAction(plusOneLabel) { onPlusOne().let { true } })
                    if (canStart) add(CustomAccessibilityAction(startLabel) { onStart().let { true } })
                }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClickLabel = stringResource(R.string.ui_list_edit_entry),
                    onLongClickLabel = stringResource(R.string.ui_open_details),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MetaLine(entry, scoreFormat)
                Text(
                    text = progressText(entry.progress, total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                canPlusOne -> PlusOneButton(
                    onClick = onPlusOne,
                    completesEntry = total != null && entry.progress + 1 >= total
                )

                canStart -> FilledTonalButton(onClick = onStart) { Text(startLabel) }
            }
        }
    }
}

/** "TV · 2023 · " followed by the score, when there is one. */
@Composable
private fun MetaLine(entry: MediaListEntry, scoreFormat: ScoreFormat) {
    val media = entry.media
    val parts = listOfNotNull(media.format?.let { stringResource(it.labelRes()) }, media.year?.toString())
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = parts.joinToString(META_SEPARATOR), style = style, color = color, maxLines = 1)
        if (entry.scoreRaw > 0) {
            if (parts.isNotEmpty()) Text(text = META_SEPARATOR, style = style, color = color)
            ScoreText(score = scoreFormat.fromRaw(entry.scoreRaw), format = scoreFormat, style = style, color = color)
        }
    }
}

private const val META_SEPARATOR = " · "

/** "18 / 28", or "18 / ?" while the total isn't known. */
@Composable
fun progressText(progress: Int, total: Int?): String = if (total != null) {
    stringResource(R.string.ui_progress, progress, total)
} else {
    stringResource(R.string.ui_progress_unknown_total, progress)
}

@ThemePreviews
@Composable
private fun MediaListRowPreview() {
    TsuzukiPreview {
        Column(
            modifier = Modifier.padding(TsuzukiSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            PreviewListEntries.all.forEach { entry ->
                MediaListRow(
                    entry = entry,
                    scoreFormat = ScoreFormat.POINT_10_DECIMAL,
                    onClick = {},
                    onLongClick = {},
                    onPlusOne = {},
                    onStart = {}
                )
            }
        }
    }
}
