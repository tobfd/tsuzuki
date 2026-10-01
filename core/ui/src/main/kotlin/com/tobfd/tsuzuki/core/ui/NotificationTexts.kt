package com.tobfd.tsuzuki.core.ui

import android.content.res.Resources
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.ListActivitySummary
import com.tobfd.tsuzuki.core.model.MediaNotificationKind
import com.tobfd.tsuzuki.core.model.Notification

/**
 * The text of [notification] without styling, for Android notifications: "Himmel and Eisen liked your
 * activity", "Episode 18 of Frieren aired". The notifications screen shows the same text with styling.
 */
fun notificationPlainText(resources: Resources, notification: Notification): String = when (notification) {
    is Notification.Airing -> resources.getString(
        R.string.ui_notification_airing,
        notification.episode.toString(),
        notification.media.title.userPreferred
    )

    is Notification.Follow -> resources.getString(R.string.ui_notification_follow, notification.user.name)

    is Notification.ActivityEvent -> {
        val first = notification.users.first().name
        when (notification.kind) {
            ActivityNotificationKind.Like -> when (notification.users.size) {
                1 -> resources.getString(R.string.ui_notification_like_one, first)

                2 -> resources.getString(R.string.ui_notification_like_two, first, notification.users[1].name)

                else -> {
                    val others = notification.users.size - 2
                    resources.getQuantityString(
                        R.plurals.ui_notification_like_many,
                        others,
                        first,
                        notification.users[1].name,
                        others
                    )
                }
            }

            ActivityNotificationKind.Reply -> resources.getString(R.string.ui_notification_reply, first)

            ActivityNotificationKind.ReplyLike -> resources.getString(R.string.ui_notification_reply_like, first)

            ActivityNotificationKind.ReplySubscribed ->
                resources.getString(R.string.ui_notification_reply_subscribed, first)

            ActivityNotificationKind.Mention -> resources.getString(R.string.ui_notification_mention, first)

            ActivityNotificationKind.Message -> resources.getString(R.string.ui_notification_message, first)
        }
    }

    is Notification.MediaEvent -> {
        val others = notification.otherTitles.joinToString(", ")
        val title = notification.media?.title?.userPreferred ?: others
        when (notification.kind) {
            MediaNotificationKind.RelatedAddition -> resources.getString(R.string.ui_notification_related, title)
            MediaNotificationKind.DataChange -> resources.getString(R.string.ui_notification_data_change, title)
            MediaNotificationKind.Merge -> resources.getString(R.string.ui_notification_merge, others, title)
            MediaNotificationKind.Deletion -> resources.getString(R.string.ui_notification_deletion, others)
        }
    }
}

/** The context under a notification: the list update an activity was about, or a moderator's reason. */
fun notificationSubline(resources: Resources, notification: Notification): String? = when (notification) {
    is Notification.ActivityEvent -> notification.listActivity?.let { listActivitySubline(resources, it) }
    is Notification.MediaEvent -> notification.reason
    else -> null
}

private fun listActivitySubline(resources: Resources, summary: ListActivitySummary): String? {
    val progress = summary.progress
    val title = summary.mediaTitle ?: return untitledListActivitySubline(resources, summary)
    val withProgress = when (summary.status) {
        "watched episode" -> R.string.ui_notification_subline_watched
        "rewatched episode" -> R.string.ui_notification_subline_rewatched
        "read chapter" -> R.string.ui_notification_subline_read
        "reread chapter" -> R.string.ui_notification_subline_reread
        else -> null
    }
    if (withProgress != null && progress != null) return resources.getString(withProgress, progress, title)
    val withoutProgress = when (summary.status) {
        "completed" -> R.string.ui_notification_subline_completed
        "plans to watch", "plans to read" -> R.string.ui_notification_subline_planning
        "dropped" -> R.string.ui_notification_subline_dropped
        "paused watching", "paused reading" -> R.string.ui_notification_subline_paused
        else -> null
    }
    return if (withoutProgress != null) resources.getString(withoutProgress, title) else title
}

/** "Watched episodes 2 - 16" when AniList left the media out; nothing for status changes without progress. */
private fun untitledListActivitySubline(resources: Resources, summary: ListActivitySummary): String? {
    val progress = summary.progress ?: return null
    val template = when (summary.status) {
        "watched episode" -> R.string.ui_notification_subline_watched_untitled
        "rewatched episode" -> R.string.ui_notification_subline_rewatched_untitled
        "read chapter" -> R.string.ui_notification_subline_read_untitled
        "reread chapter" -> R.string.ui_notification_subline_reread_untitled
        else -> return null
    }
    return resources.getString(template, progress)
}
