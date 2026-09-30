package com.tobfd.tsuzuki.core.model

import java.time.Instant

/** Titles of a media. [userPreferred] already follows the viewer's title language. */
data class MediaTitle(val userPreferred: String, val romaji: String?, val english: String?, val native: String?) {
    /** Every known title, for searching. */
    val all: List<String>
        get() = listOfNotNull(userPreferred, romaji, english, native).distinct()
}

/** The small part of a media that list rows, covers and the list editor need. */
data class MediaLite(
    val id: Int,
    val type: MediaType,
    val format: MediaFormat?,
    val status: MediaStatus?,
    val episodes: Int?,
    val chapters: Int?,
    val volumes: Int?,
    val title: MediaTitle,
    val coverUrl: String?,
    /** AniList's `coverImage.color`, e.g. "#e4a15d". */
    val coverColor: String?,
    /** Season year for anime, else the start year. */
    val year: Int?,
    val averageScore: Int?,
    /** The next episode to air, for airing anime. */
    val nextAiringEpisode: Int?,
    val isAdult: Boolean,
    /** When [nextAiringEpisode] airs. */
    val nextAiringAt: Instant? = null
) {
    /** Episodes for anime, chapters for manga; null while unknown. */
    val total: Int?
        get() = when (type) {
            MediaType.ANIME -> episodes
            MediaType.MANGA -> chapters
        }
}
