package com.tobfd.tsuzuki.core.model

/** Status of an entry on a user's list, mirroring AniList's `MediaListStatus`. */
enum class MediaListStatus {
    /** Watching or reading. */
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,

    /** Rewatching or rereading. */
    REPEATING
}
