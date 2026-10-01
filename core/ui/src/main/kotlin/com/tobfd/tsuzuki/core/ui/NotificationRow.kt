package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.ListActivitySummary
import com.tobfd.tsuzuki.core.model.MediaNotificationKind
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.UserLite
import java.time.Instant

/** Avatars shown side by side for grouped likes. */
private const val MAX_STACKED_AVATARS = 3

/** Lines of a moderator's reason under a media notification. */
private const val SUBLINE_MAX_LINES = 2

/**
 * One notification (docs/DESIGN.md, Notifications): stacked avatars or the cover, the text with names in
 * bold, a subline with context, the relative time, and an unread dot with a tinted background when unread.
 * TalkBack reads the row as one item.
 */
@Composable
fun NotificationRow(entry: NotificationEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val notification = entry.notification
    val unreadLabel = stringResource(R.string.ui_notification_unread)
    val colors = MaterialTheme.colorScheme
    val background = if (entry.isUnread) colors.surfaceContainerHigh else colors.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = TsuzukiSizes.minTouchTarget)
            .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        verticalAlignment = Alignment.Top
    ) {
        // Covers and avatars centred in the leading column, so rows line up whatever they start with.
        Box(modifier = Modifier.width(TsuzukiSizes.notificationLeading), contentAlignment = Alignment.TopCenter) {
            NotificationLeading(notification, background)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraExtraSmall)
        ) {
            Text(text = notificationText(notification), style = MaterialTheme.typography.bodyMedium)
            notificationSubline(notification)?.let { subline ->
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = SUBLINE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = relativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (entry.isUnread) {
            Box(
                modifier = Modifier
                    .padding(top = TsuzukiSpacing.extraSmall)
                    .size(TsuzukiSizes.statusDot)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = unreadLabel }
            )
        }
    }
}

@Composable
private fun NotificationLeading(notification: Notification, background: Color) {
    when (notification) {
        is Notification.Airing -> NotificationCover(notification.media.coverUrl, notification.media.coverColor)

        is Notification.Follow -> AvatarStack(listOf(notification.user), background)

        is Notification.ActivityEvent -> AvatarStack(notification.users, background)

        is Notification.MediaEvent -> {
            val media = notification.media
            if (media != null) {
                NotificationCover(media.coverUrl, media.coverColor)
            } else {
                Icon(
                    painter = painterResource(TsuzukiIcons.Info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(TsuzukiSizes.notificationAvatar)
                )
            }
        }
    }
}

@Composable
private fun NotificationCover(url: String?, color: String?) {
    MediaCover(
        imageUrl = url,
        contentDescription = null,
        placeholderColor = coverColorOrNull(color),
        modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
    )
}

/**
 * Up to three avatars, each [TsuzukiSizes.notificationAvatarOffset] right of the one before, ringed in
 * the row's [background] so the overlap reads on unread (tinted) rows too.
 */
@Composable
private fun AvatarStack(users: List<UserLite>, background: Color) {
    Box {
        users.take(MAX_STACKED_AVATARS).forEachIndexed { index, user ->
            UserAvatar(
                avatarUrl = user.avatarUrl,
                name = user.name,
                size = TsuzukiSizes.notificationAvatar,
                modifier = Modifier
                    .padding(start = TsuzukiSizes.notificationAvatarOffset * index)
                    .border(2.dp, background, CircleShape)
            )
        }
    }
}

@Composable
private fun primaryTitle(title: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)) { append(title) }
}

@Composable
private fun template(id: Int, vararg parts: AnnotatedString): AnnotatedString {
    val markers = Array(parts.size) { (it + 1).toChar().toString() }
    return fillTemplate(stringResource(id, *markers), *parts)
}

/** "**Himmel**, **Eisen** and 3 others liked your activity", "Episode 18 of **Frieren** aired". */
@Composable
internal fun notificationText(notification: Notification): AnnotatedString = when (notification) {
    is Notification.Airing -> template(
        R.string.ui_notification_airing,
        AnnotatedString(notification.episode.toString()),
        primaryTitle(notification.media.title.userPreferred)
    )

    is Notification.Follow -> template(R.string.ui_notification_follow, bold(notification.user.name))

    is Notification.ActivityEvent -> activityEventText(notification)

    is Notification.MediaEvent -> {
        val title = notification.media?.title?.userPreferred?.let { primaryTitle(it) }
        val others = bold(notification.otherTitles.joinToString(", "))
        when (notification.kind) {
            MediaNotificationKind.RelatedAddition -> template(R.string.ui_notification_related, title ?: others)
            MediaNotificationKind.DataChange -> template(R.string.ui_notification_data_change, title ?: others)
            MediaNotificationKind.Merge -> template(R.string.ui_notification_merge, others, title ?: others)
            MediaNotificationKind.Deletion -> template(R.string.ui_notification_deletion, others)
        }
    }
}

@Composable
private fun activityEventText(event: Notification.ActivityEvent): AnnotatedString {
    val first = bold(event.users.first().name)
    return when (event.kind) {
        ActivityNotificationKind.Like -> when (event.users.size) {
            1 -> template(R.string.ui_notification_like_one, first)

            2 -> template(R.string.ui_notification_like_two, first, bold(event.users[1].name))

            else -> {
                val others = event.users.size - 2
                fillTemplate(
                    pluralStringResource(R.plurals.ui_notification_like_many, others, "\u0001", "\u0002", others),
                    first,
                    bold(event.users[1].name)
                )
            }
        }

        ActivityNotificationKind.Reply -> template(R.string.ui_notification_reply, first)

        ActivityNotificationKind.ReplyLike -> template(R.string.ui_notification_reply_like, first)

        ActivityNotificationKind.ReplySubscribed -> template(R.string.ui_notification_reply_subscribed, first)

        ActivityNotificationKind.Mention -> template(R.string.ui_notification_mention, first)

        ActivityNotificationKind.Message -> template(R.string.ui_notification_message, first)
    }
}

/** The context under the text: the list update the activity was about, or a moderator's reason. */
@Composable
private fun notificationSubline(notification: Notification): String? = when (notification) {
    is Notification.ActivityEvent -> notification.listActivity?.let { listActivitySubline(it) }
    is Notification.MediaEvent -> notification.reason
    else -> null
}

@Composable
private fun listActivitySubline(summary: ListActivitySummary): String? {
    val progress = summary.progress
    val title = summary.mediaTitle ?: return untitledListActivitySubline(summary)
    val withProgress = when (summary.status) {
        "watched episode" -> R.string.ui_notification_subline_watched
        "rewatched episode" -> R.string.ui_notification_subline_rewatched
        "read chapter" -> R.string.ui_notification_subline_read
        "reread chapter" -> R.string.ui_notification_subline_reread
        else -> null
    }
    if (withProgress != null && progress != null) return stringResource(withProgress, progress, title)
    val withoutProgress = when (summary.status) {
        "completed" -> R.string.ui_notification_subline_completed
        "plans to watch", "plans to read" -> R.string.ui_notification_subline_planning
        "dropped" -> R.string.ui_notification_subline_dropped
        "paused watching", "paused reading" -> R.string.ui_notification_subline_paused
        else -> null
    }
    return if (withoutProgress != null) stringResource(withoutProgress, title) else title
}

/** "Watched episodes 2 - 16" when AniList left the media out; nothing for status changes without progress. */
@Composable
private fun untitledListActivitySubline(summary: ListActivitySummary): String? {
    val progress = summary.progress ?: return null
    val template = when (summary.status) {
        "watched episode" -> R.string.ui_notification_subline_watched_untitled
        "rewatched episode" -> R.string.ui_notification_subline_rewatched_untitled
        "read chapter" -> R.string.ui_notification_subline_read_untitled
        "reread chapter" -> R.string.ui_notification_subline_reread_untitled
        else -> return null
    }
    return stringResource(template, progress)
}

@ThemePreviews
@Composable
private fun NotificationRowPreview() {
    val now = Instant.now()
    val users = listOf(UserLite(1, "Himmel", null), UserLite(2, "Eisen", null), UserLite(3, "Fern", null))
    val summary = ListActivitySummary("watched episode", "17 - 18", 154587, "Frieren")
    TsuzukiPreview {
        Column {
            NotificationRow(
                entry = NotificationEntry(
                    Notification.ActivityEvent(
                        1,
                        now.minusSeconds(600),
                        ActivityNotificationKind.Like,
                        users + users.map { it.copy(id = it.id + 10) },
                        9,
                        summary
                    ),
                    isUnread = true
                ),
                onClick = {}
            )
            NotificationRow(
                entry = NotificationEntry(
                    Notification.Airing(2, now.minusSeconds(7_200), 18, PreviewListEntries.frieren.media),
                    isUnread = false
                ),
                onClick = {}
            )
            NotificationRow(
                entry = NotificationEntry(Notification.Follow(3, now.minusSeconds(86_400), users[2]), isUnread = false),
                onClick = {}
            )
        }
    }
}
