package com.tobfd.tsuzuki.core.model

/**
 * Release status of an anime or manga, mirroring AniList's `MediaStatus`. Not to be confused with
 * [MediaListStatus], the status of an entry on a user's list.
 */
enum class MediaStatus {
    FINISHED,
    RELEASING,
    NOT_YET_RELEASED,
    CANCELLED,
    HIATUS
}
