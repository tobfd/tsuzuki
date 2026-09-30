package com.tobfd.tsuzuki.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tobfd.tsuzuki.core.designsystem.component.StatusDot
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaDetail
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.PersonLite
import com.tobfd.tsuzuki.core.ui.AniListHtmlText
import com.tobfd.tsuzuki.core.ui.MediaCoverCard
import com.tobfd.tsuzuki.core.ui.UserAvatar
import com.tobfd.tsuzuki.core.ui.characterRoleLabel
import com.tobfd.tsuzuki.core.ui.formatDate
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.progressText
import com.tobfd.tsuzuki.core.ui.score.ScoreText
import com.tobfd.tsuzuki.core.ui.statusColor

private const val VISIBLE_TAGS = 8
private val PersonImageSize = 48.dp
private val ScoreBarHeight = 96.dp

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = TsuzukiSpacing.screenMargin)
    )
}

/** Section frame: title, then [content], with the gap between sections below. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(top = TsuzukiSpacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
    ) {
        SectionTitle(title)
        content()
    }
}

private val horizontalMargin = Modifier.padding(horizontal = TsuzukiSpacing.screenMargin)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewSection(detail: MediaDetail, onOpenMedia: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Section(stringResource(R.string.media_tab_overview)) {
        detail.descriptionHtml?.let { AniListHtmlText(html = it, modifier = horizontalMargin) }
        if (detail.genres.isNotEmpty()) {
            FlowRow(modifier = horizontalMargin, horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
                detail.genres.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
            }
        }
        Tags(detail)
        InfoGrid(detail, modifier = horizontalMargin)
        if (detail.streamingLinks.isNotEmpty() || detail.trailer?.url != null) {
            Text(
                text = stringResource(R.string.media_where_to_watch),
                style = MaterialTheme.typography.titleMedium,
                modifier = horizontalMargin
            )
            FlowRow(modifier = horizontalMargin, horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
                detail.trailer?.url?.let { url ->
                    AssistChip(
                        onClick = { uriHandler.openUri(url) },
                        label = { Text(stringResource(R.string.media_trailer)) },
                        leadingIcon = { Icon(painterResource(TsuzukiIcons.PlayArrow), contentDescription = null) }
                    )
                }
                detail.streamingLinks.forEach { link ->
                    OutlinedButton(onClick = { uriHandler.openUri(link.url) }) { Text(link.site) }
                }
            }
        }
        if (detail.relations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.media_relations),
                style = MaterialTheme.typography.titleMedium,
                modifier = horizontalMargin
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = TsuzukiSpacing.screenMargin),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)
            ) {
                items(detail.relations, key = { "${it.type}-${it.media.id}" }) { relation ->
                    MediaCoverCard(
                        media = relation.media,
                        onClick = { onOpenMedia(relation.media.id) },
                        label = relationLabel(relation.type),
                        meta = listOfNotNull(
                            relation.media.format?.let { stringResource(it.labelRes()) },
                            relation.media.status?.let { stringResource(it.labelRes()) }
                        ).joinToString(" · ").ifEmpty { null }
                    )
                }
            }
        }
    }
}

/** Genres' neighbour: tags with rank %, the first few, "+N tags", spoilers behind a button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(detail: MediaDetail) {
    if (detail.tags.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var spoilers by rememberSaveable { mutableStateOf(false) }
    val safe = detail.tags.filterNot { it.isSpoiler }
    val shown = (if (spoilers) detail.tags else safe).let { if (expanded) it else it.take(VISIBLE_TAGS) }
    val hidden = (if (spoilers) detail.tags.size else safe.size) - shown.size
    FlowRow(
        modifier = horizontalMargin,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
    ) {
        shown.forEach { tag ->
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        text = tag.rank?.let { stringResource(R.string.media_tag_rank, tag.name, it) } ?: tag.name,
                        color = if (tag.isSpoiler) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
            )
        }
        if (hidden > 0) {
            val moreTags = pluralStringResource(R.plurals.media_more_tags, hidden, hidden)
            TextButton(onClick = { expanded = true }) { Text(moreTags) }
        }
        if (detail.tags.any { it.isSpoiler }) {
            TextButton(onClick = { spoilers = !spoilers }) {
                val label = if (spoilers) R.string.media_hide_spoiler_tags else R.string.media_show_spoiler_tags
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun InfoGrid(detail: MediaDetail, modifier: Modifier = Modifier) {
    val media = detail.media
    val info = detail.info
    val rows = listOfNotNull(
        media.format?.let { stringResource(R.string.media_info_format) to stringResource(it.labelRes()) },
        media.total?.let {
            stringResource(
                if (media.type ==
                    MediaType.ANIME
                ) {
                    R.string.media_info_episodes
                } else {
                    R.string.media_info_chapters
                }
            ) to
                it.toString()
        },
        info.episodeDuration?.let {
            stringResource(R.string.media_info_duration) to
                stringResource(R.string.media_minutes, it)
        },
        media.status?.let { stringResource(R.string.media_info_status) to stringResource(it.labelRes()) },
        info.startDate?.let { stringResource(R.string.media_info_start) to formatDate(it) },
        info.endDate?.let { stringResource(R.string.media_info_end) to formatDate(it) },
        info.season?.let { season ->
            stringResource(R.string.media_info_season) to
                listOfNotNull(stringResource(season.labelRes()), media.year?.toString()).joinToString(" ")
        },
        info.studios.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.media_info_studio) to
                it.joinToString(", ")
        },
        info.source?.let { stringResource(R.string.media_info_source) to sourceLabel(it) },
        info.popularity?.let { stringResource(R.string.media_info_popularity) to "%,d".format(it) },
        info.favourites?.let { stringResource(R.string.media_info_favourites) to "%,d".format(it) }
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun CharactersSection(detail: MediaDetail, onOpenCharacter: (Int) -> Unit, onOpenStaff: (Int) -> Unit) {
    Section(stringResource(R.string.media_tab_characters)) {
        if (detail.characters.isEmpty()) {
            EmptyText(stringResource(R.string.media_no_characters))
        }
        detail.characters.forEach { role ->
            Row(modifier = horizontalMargin.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PersonCell(
                    person = role.character,
                    detail = role.role?.let { characterRoleLabel(it) },
                    onClick = { onOpenCharacter(role.character.id) },
                    modifier = Modifier.weight(1f)
                )
                role.voiceActor?.let { actor ->
                    PersonCell(
                        person = actor,
                        detail = stringResource(R.string.media_japanese),
                        onClick = { onOpenStaff(actor.id) },
                        modifier = Modifier.weight(1f),
                        alignEnd = true
                    )
                }
            }
        }
        if (detail.staff.isNotEmpty()) {
            Text(
                text = stringResource(R.string.media_staff),
                style = MaterialTheme.typography.titleMedium,
                modifier = horizontalMargin
            )
            detail.staff.forEach { role ->
                PersonCell(
                    person = role.staff,
                    detail = role.role,
                    onClick = { onOpenStaff(role.staff.id) },
                    modifier = horizontalMargin.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PersonCell(
    person: PersonLite,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            TsuzukiSpacing.medium,
            if (alignEnd) Alignment.End else Alignment.Start
        )
    ) {
        if (!alignEnd) PersonImage(person)
        Column(
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (alignEnd) PersonImage(person)
    }
}

@Composable
private fun PersonImage(person: PersonLite) {
    AsyncImage(
        model = person.imageUrl,
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .size(PersonImageSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@Composable
internal fun StatsSection(detail: MediaDetail) {
    Section(stringResource(R.string.media_tab_stats)) {
        val statuses = MediaListStatus.entries.mapNotNull { status ->
            detail.statusDistribution[status]?.takeIf { it > 0 }?.let {
                status to
                    it
            }
        }
        if (statuses.isEmpty() && detail.scoreDistribution.isEmpty()) {
            EmptyText(stringResource(R.string.media_no_stats))
            return@Section
        }
        if (statuses.isNotEmpty()) {
            val total = statuses.sumOf { it.second }.toFloat()
            Row(
                modifier = horizontalMargin
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
            ) {
                statuses.forEach { (status, amount) ->
                    Box(Modifier.weight(amount / total).fillMaxHeight().background(status.statusColor().color))
                }
            }
            Column(modifier = horizontalMargin, verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
                statuses.forEach { (status, amount) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
                    ) {
                        StatusDot(status.statusColor())
                        Text(
                            text = stringResource(status.labelRes(detail.media.type)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = pluralStringResource(R.plurals.media_users, amount, "%,d".format(amount)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (detail.scoreDistribution.isNotEmpty()) {
            Text(
                text = stringResource(R.string.media_score_distribution),
                style = MaterialTheme.typography.titleMedium,
                modifier = horizontalMargin
            )
            val max = detail.scoreDistribution.maxOf { it.second }.coerceAtLeast(1)
            Row(
                modifier = horizontalMargin.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall),
                verticalAlignment = Alignment.Bottom
            ) {
                detail.scoreDistribution.forEach { (score, amount) ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ScoreBarHeight * (amount.toFloat() / max).coerceAtLeast(0.02f))
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = score.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SocialSection(state: MediaDetailUiState.Content) {
    Section(stringResource(R.string.media_tab_social)) {
        when {
            state.viewer == null -> EmptyText(stringResource(R.string.media_social_guest))

            state.detail.following.isEmpty() -> EmptyText(stringResource(R.string.media_social_empty))

            else -> state.detail.following.forEach { item ->
                Row(
                    modifier = horizontalMargin.fillMaxWidth().semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
                ) {
                    UserAvatar(avatarUrl = item.user.avatarUrl, name = item.user.name, size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.user.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${stringResource(item.status.labelRes(state.detail.media.type))} · " +
                                progressText(item.progress, state.detail.media.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ScoreText(
                        score = item.score,
                        format = state.scoreFormat,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecommendationsSection(detail: MediaDetail, onOpenMedia: (Int) -> Unit) {
    Section(stringResource(R.string.media_tab_recommendations)) {
        if (detail.recommendations.isEmpty()) {
            EmptyText(stringResource(R.string.media_no_recommendations))
            return@Section
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = TsuzukiSpacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)
        ) {
            items(detail.recommendations, key = { it.media.id }) { recommendation ->
                MediaCoverCard(
                    media = recommendation.media,
                    onClick = { onOpenMedia(recommendation.media.id) },
                    meta = stringResource(R.string.media_recommendation_votes, recommendation.rating)
                )
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = horizontalMargin
    )
}
