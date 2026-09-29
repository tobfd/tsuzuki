package com.tobfd.tsuzuki.core.model

import java.time.Instant

/** A user as shown in feeds, lists and headers. */
data class UserLite(val id: Int, val name: String, val avatarUrl: String?)

/** An entry of the activity feed (docs/DESIGN.md, `ActivityCard`). */
sealed interface Activity {
    val id: Int
    val user: UserLite
    val createdAt: Instant
    val likeCount: Int
    val isLiked: Boolean
    val replyCount: Int
    val siteUrl: String?

    /** A list update such as "watched episode 18 of Frieren". */
    data class ListUpdate(
        override val id: Int,
        override val user: UserLite,
        override val createdAt: Instant,
        override val likeCount: Int,
        override val isLiked: Boolean,
        override val replyCount: Int,
        override val siteUrl: String?,
        /** AniList's wording, e.g. "watched episode", "completed", "plans to watch". */
        val status: String,
        /** "18" or "17 - 18"; null for status changes without progress. */
        val progress: String?,
        val media: MediaLite
    ) : Activity

    /** A status post; [html] is AniList's small HTML subset. */
    data class Text(
        override val id: Int,
        override val user: UserLite,
        override val createdAt: Instant,
        override val likeCount: Int,
        override val isLiked: Boolean,
        override val replyCount: Int,
        override val siteUrl: String?,
        val html: String
    ) : Activity
}
