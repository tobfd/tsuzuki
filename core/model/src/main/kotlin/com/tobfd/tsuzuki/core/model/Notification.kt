package com.tobfd.tsuzuki.core.model

import java.time.Instant

/** The filter chips of the notifications screen (docs/DESIGN.md, Notifications). */
enum class NotificationFilter {
    All,
    Airing,
    Activity,
    Follows,
    Media
}

/** What happened to or on the viewer's activity. */
enum class ActivityNotificationKind {
    Like,
    Reply,
    ReplyLike,
    ReplySubscribed,
    Mention,
    Message
}

/** Site changes to a media on the viewer's list. */
enum class MediaNotificationKind {
    RelatedAddition,
    DataChange,
    Merge,
    Deletion
}

/** A list update a notification refers to, for the subline ("Watched episodes 17 - 18 of Frieren"). */
data class ListActivitySummary(
    /** AniList's wording, e.g. "watched episode". */
    val status: String,
    /** "18" or "17 - 18"; null for status changes without progress. */
    val progress: String?,
    val mediaId: Int,
    val mediaTitle: String
)

/** One notification of the viewer. The types the app doesn't show (forum, submissions) never get here. */
sealed interface Notification {
    val id: Int
    val createdAt: Instant

    /** Episode [episode] of [media] aired. */
    data class Airing(override val id: Int, override val createdAt: Instant, val episode: Int, val media: MediaLite) :
        Notification

    /** [user] started following the viewer. */
    data class Follow(override val id: Int, override val createdAt: Instant, val user: UserLite) : Notification

    /**
     * Something happened on activity [activityId]. [users] has one user, except for likes: consecutive likes on
     * the same activity are grouped into one notification, newest first, with the id and time of the newest.
     */
    data class ActivityEvent(
        override val id: Int,
        override val createdAt: Instant,
        val kind: ActivityNotificationKind,
        val users: List<UserLite>,
        val activityId: Int,
        /** Set when the activity is a list update. */
        val listActivity: ListActivitySummary?
    ) : Notification

    /** A site change to [media] (null once it was deleted); [reason] is the moderator's note. */
    data class MediaEvent(
        override val id: Int,
        override val createdAt: Instant,
        val kind: MediaNotificationKind,
        val media: MediaLite?,
        /** The deleted title, or the titles merged into [media]. */
        val otherTitles: List<String>,
        val reason: String?
    ) : Notification
}

/** A notification as the list shows it: unread ones are highlighted (docs/ROADMAP.md, M10). */
data class NotificationEntry(val notification: Notification, val isUnread: Boolean)
