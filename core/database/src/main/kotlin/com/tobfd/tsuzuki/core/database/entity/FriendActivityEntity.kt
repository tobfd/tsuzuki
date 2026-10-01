package com.tobfd.tsuzuki.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One of the newest activities of the people the viewer follows, for the "Friends' activity" widget
 * (since database version 2). The table holds only the last fetched page and is replaced as a whole.
 */
@Entity(tableName = "friend_activity")
data class FriendActivityEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "user_avatar_url") val userAvatarUrl: String?,
    /** Epoch seconds. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** A list update: AniList's wording, e.g. "watched episode"; null for a text activity. */
    val status: String?,
    val progress: String?,
    /** A text activity: AniList's HTML; null for a list update. */
    val html: String?,
    @ColumnInfo(name = "media_id") val mediaId: Int?,
    @ColumnInfo(name = "media_type") val mediaType: String?,
    @ColumnInfo(name = "media_title") val mediaTitle: String?,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "cover_color") val coverColor: String?,
    /** When this page was fetched, epoch milliseconds; the same for every row. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long
)
