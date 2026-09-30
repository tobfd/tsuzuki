package com.tobfd.tsuzuki.core.data.notifications

import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.ListActivitySummary
import com.tobfd.tsuzuki.core.model.MediaNotificationKind
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.network.NotificationsQuery
import com.tobfd.tsuzuki.core.network.fragment.NotificationActivity
import com.tobfd.tsuzuki.core.network.type.NotificationType
import java.time.Instant

/** The `type_in` of each filter chip; null (All) asks for every type. */
internal fun NotificationFilter.types(): List<NotificationType>? = when (this) {
    NotificationFilter.All -> null

    NotificationFilter.Airing -> listOf(NotificationType.AIRING)

    NotificationFilter.Activity -> listOf(
        NotificationType.ACTIVITY_MESSAGE,
        NotificationType.ACTIVITY_REPLY,
        NotificationType.ACTIVITY_MENTION,
        NotificationType.ACTIVITY_LIKE,
        NotificationType.ACTIVITY_REPLY_LIKE,
        NotificationType.ACTIVITY_REPLY_SUBSCRIBED
    )

    NotificationFilter.Follows -> listOf(NotificationType.FOLLOWING)

    NotificationFilter.Media -> listOf(
        NotificationType.RELATED_MEDIA_ADDITION,
        NotificationType.MEDIA_DATA_CHANGE,
        NotificationType.MEDIA_MERGE,
        NotificationType.MEDIA_DELETION
    )
}

private fun time(createdAt: Int?): Instant = Instant.ofEpochSecond(createdAt?.toLong() ?: 0L)

private fun NotificationActivity?.summary(): ListActivitySummary? {
    val list = this?.onListActivity ?: return null
    val media = list.media ?: return null
    return ListActivitySummary(
        status = list.status ?: return null,
        progress = list.progress?.takeIf { it.isNotBlank() },
        mediaId = media.id,
        mediaTitle = media.title?.userPreferred ?: return null
    )
}

private fun activityEvent(
    id: Int,
    createdAt: Int?,
    kind: ActivityNotificationKind,
    user: UserLite?,
    activityId: Int,
    activity: NotificationActivity?
): Notification? = Notification.ActivityEvent(
    id = id,
    createdAt = time(createdAt),
    kind = kind,
    users = listOf(user ?: return null),
    activityId = activityId,
    listActivity = activity.summary()
)

/**
 * The app's model of one notification; null for types the app doesn't show (forum, submissions) and for
 * notifications missing the user or media they are about.
 */
internal fun NotificationsQuery.Notification.toModel(): Notification? {
    onAiringNotification?.let {
        return Notification.Airing(it.id, time(it.createdAt), it.episode, it.media?.mediaCard?.toModel() ?: return null)
    }
    onFollowingNotification?.let {
        return Notification.Follow(it.id, time(it.createdAt), it.user?.userLite?.toModel() ?: return null)
    }
    onActivityLikeNotification?.let {
        val user = it.user?.userLite?.toModel()
        val activity = it.activity?.notificationActivity
        return activityEvent(it.id, it.createdAt, ActivityNotificationKind.Like, user, it.activityId, activity)
    }
    onActivityReplyNotification?.let {
        val user = it.user?.userLite?.toModel()
        val activity = it.activity?.notificationActivity
        return activityEvent(it.id, it.createdAt, ActivityNotificationKind.Reply, user, it.activityId, activity)
    }
    onActivityReplyLikeNotification?.let {
        val user = it.user?.userLite?.toModel()
        val activity = it.activity?.notificationActivity
        return activityEvent(it.id, it.createdAt, ActivityNotificationKind.ReplyLike, user, it.activityId, activity)
    }
    onActivityReplySubscribedNotification?.let {
        val user = it.user?.userLite?.toModel()
        val activity = it.activity?.notificationActivity
        return activityEvent(
            it.id,
            it.createdAt,
            ActivityNotificationKind.ReplySubscribed,
            user,
            it.activityId,
            activity
        )
    }
    onActivityMentionNotification?.let {
        val user = it.user?.userLite?.toModel()
        val activity = it.activity?.notificationActivity
        return activityEvent(it.id, it.createdAt, ActivityNotificationKind.Mention, user, it.activityId, activity)
    }
    onActivityMessageNotification?.let {
        val user = it.user?.userLite?.toModel()
        return activityEvent(it.id, it.createdAt, ActivityNotificationKind.Message, user, it.activityId, null)
    }
    onRelatedMediaAdditionNotification?.let {
        val media = it.media?.mediaCard?.toModel() ?: return null
        return Notification.MediaEvent(
            it.id,
            time(it.createdAt),
            MediaNotificationKind.RelatedAddition,
            media,
            emptyList(),
            null
        )
    }
    onMediaDataChangeNotification?.let {
        val media = it.media?.mediaCard?.toModel() ?: return null
        return Notification.MediaEvent(
            it.id,
            time(it.createdAt),
            MediaNotificationKind.DataChange,
            media,
            emptyList(),
            it.reason?.takeIf { reason -> reason.isNotBlank() }
        )
    }
    onMediaMergeNotification?.let {
        val media = it.media?.mediaCard?.toModel() ?: return null
        return Notification.MediaEvent(
            it.id,
            time(it.createdAt),
            MediaNotificationKind.Merge,
            media,
            it.deletedMediaTitles.orEmpty().filterNotNull(),
            it.reason?.takeIf { reason -> reason.isNotBlank() }
        )
    }
    onMediaDeletionNotification?.let {
        return Notification.MediaEvent(
            it.id,
            time(it.createdAt),
            MediaNotificationKind.Deletion,
            null,
            listOfNotNull(it.deletedMediaTitle),
            it.reason?.takeIf { reason -> reason.isNotBlank() }
        )
    }
    return null
}
