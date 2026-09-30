package com.tobfd.tsuzuki.core.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.UserLite
import java.time.Instant

private val ActivityAvatarSize = 40.dp
private const val TEXT_ACTIVITY_MAX_LINES = 6

/**
 * An activity in a feed (docs/DESIGN.md, `ActivityCard`): avatar, "tobfd watched episode 18 of
 * Frieren", the cover on the right, relative time, like button with count and the reply count.
 */
@Composable
fun ActivityCard(
    activity: Activity,
    onLikeClick: () -> Unit,
    onUserClick: () -> Unit,
    onMediaClick: (mediaId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(TsuzukiSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
        ) {
            UserAvatar(
                avatarUrl = activity.user.avatarUrl,
                name = activity.user.name,
                size = ActivityAvatarSize,
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.ui_activity_open_profile),
                    onClick = onUserClick
                )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
            ) {
                when (activity) {
                    is Activity.ListUpdate -> Text(
                        text = listUpdateText(activity),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onMediaClick(activity.media.id) }
                    )

                    is Activity.Text -> {
                        Text(
                            text = bold(activity.user.name),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable(onClick = onUserClick)
                        )
                        val imageLabel = stringResource(R.string.ui_image_link)
                        Text(
                            // Images and embeds show as links; v1 loads no media from user text.
                            text = remember(activity.html, imageLabel) {
                                AnnotatedString.fromHtml(withImagesAsLinks(activity.html, imageLabel))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = TEXT_ACTIVITY_MAX_LINES,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ActivityFooter(activity, onLikeClick)
            }
            if (activity is Activity.ListUpdate) {
                MediaCover(
                    imageUrl = activity.media.coverUrl,
                    contentDescription = null,
                    placeholderColor = coverColorOrNull(activity.media.coverColor),
                    modifier = Modifier
                        .width(TsuzukiSizes.listThumbnail.width - 8.dp)
                        .clickable { onMediaClick(activity.media.id) }
                )
            }
        }
    }
}

@Composable
private fun ActivityFooter(activity: Activity, onLikeClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = relativeTime(activity.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.weight(1f)
        )
        val likeLabel = stringResource(if (activity.isLiked) R.string.ui_activity_unlike else R.string.ui_activity_like)
        IconToggleButton(
            checked = activity.isLiked,
            onCheckedChange = { onLikeClick() },
            modifier = Modifier.semantics { contentDescription = likeLabel }
        ) {
            Icon(
                painter = painterResource(if (activity.isLiked) TsuzukiIcons.FavoriteFilled else TsuzukiIcons.Favorite),
                contentDescription = null,
                tint = if (activity.isLiked) MaterialTheme.colorScheme.primary else color,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(text = activity.likeCount.toString(), style = MaterialTheme.typography.labelMedium, color = color)
        Icon(
            painter = painterResource(TsuzukiIcons.ChatBubble),
            contentDescription = stringResource(R.string.ui_activity_replies),
            tint = color,
            modifier = Modifier
                .padding(start = TsuzukiSpacing.medium, end = TsuzukiSpacing.extraSmall)
                .size(18.dp)
        )
        Text(text = activity.replyCount.toString(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** "Just now", "5 minutes ago", "Yesterday", in the app language. */
@Composable
fun relativeTime(instant: Instant): String {
    val now = System.currentTimeMillis()
    val then = instant.toEpochMilli()
    if (now - then < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.ui_just_now)
    return DateUtils.getRelativeTimeSpanString(then, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
        .toString()
}

@Composable
private fun bold(text: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
}

/** "**tobfd** watched episode 18 of **Frieren**", with the title in primary. */
@Composable
private fun listUpdateText(activity: Activity.ListUpdate): AnnotatedString {
    val user = bold(activity.user.name)
    val title = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)) {
            append(activity.media.title.userPreferred)
        }
    }
    val progress = activity.progress
    val template = listUpdateTemplate(activity.status, progress != null)
    return if (template == null) {
        buildAnnotatedString {
            append(user)
            append(" ${activity.status} ")
            if (progress != null) append("$progress ")
            append(title)
        }
    } else if (progress != null) {
        fillTemplate(stringResource(template, "\u0001", "\u0002", "\u0003"), user, AnnotatedString(progress), title)
    } else {
        fillTemplate(stringResource(template, "\u0001", "\u0002"), user, title)
    }
}

/** The wording for AniList's list activity statuses; null for ones the app doesn't know. */
private fun listUpdateTemplate(status: String, withProgress: Boolean): Int? = when (status to withProgress) {
    "watched episode" to true -> R.string.ui_activity_watched_episode
    "rewatched episode" to true -> R.string.ui_activity_rewatched_episode
    "read chapter" to true -> R.string.ui_activity_read_chapter
    "reread chapter" to true -> R.string.ui_activity_reread_chapter
    "completed" to false -> R.string.ui_activity_completed
    "rewatched" to false -> R.string.ui_activity_rewatched
    "reread" to false -> R.string.ui_activity_reread
    "plans to watch" to false -> R.string.ui_activity_plans_to_watch
    "plans to read" to false -> R.string.ui_activity_plans_to_read
    "dropped" to false -> R.string.ui_activity_dropped
    "paused watching" to false -> R.string.ui_activity_paused
    "paused reading" to false -> R.string.ui_activity_paused
    else -> null
}

/** Replaces the markers \u0001, \u0002, \u0003 in [template] with [parts], keeping their styles. */
private fun fillTemplate(template: String, vararg parts: AnnotatedString): AnnotatedString = buildAnnotatedString {
    var rest = template
    while (rest.isNotEmpty()) {
        val index = rest.indexOfFirst { it in '\u0001'..'\u0003' }
        if (index < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, index))
        parts.getOrNull(rest[index] - '\u0001')?.let { append(it) }
        rest = rest.substring(index + 1)
    }
}

@ThemePreviews
@Composable
private fun ActivityCardPreview() {
    val user = UserLite(1, "tobfd", null)
    TsuzukiPreview {
        Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
            ActivityCard(
                activity = Activity.ListUpdate(
                    id = 1,
                    user = user,
                    createdAt = Instant.now().minusSeconds(600),
                    likeCount = 3,
                    isLiked = true,
                    replyCount = 1,
                    siteUrl = null,
                    status = "watched episode",
                    progress = "18",
                    media = PreviewListEntries.frieren.media
                ),
                onLikeClick = {},
                onUserClick = {},
                onMediaClick = {}
            )
            ActivityCard(
                activity = Activity.Text(
                    id = 2,
                    user = user,
                    createdAt = Instant.now().minusSeconds(86_400),
                    likeCount = 0,
                    isLiked = false,
                    replyCount = 0,
                    siteUrl = null,
                    html = "Frieren episode 18 was <b>so good</b>."
                ),
                onLikeClick = {},
                onUserClick = {},
                onMediaClick = {}
            )
        }
    }
}
