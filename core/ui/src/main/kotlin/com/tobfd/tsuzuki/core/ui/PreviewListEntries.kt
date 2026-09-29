package com.tobfd.tsuzuki.core.ui

import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaFormat
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaTitle
import com.tobfd.tsuzuki.core.model.MediaType
import java.time.Instant

/** List entries for previews and the component catalog, shaped like real AniList data. */
object PreviewListEntries {

    private fun entry(
        id: Int,
        title: String,
        format: MediaFormat,
        year: Int,
        status: MediaListStatus,
        progress: Int,
        total: Int?,
        scoreRaw: Int,
        color: String,
        type: MediaType = MediaType.ANIME
    ) = MediaListEntry(
        id = id,
        mediaId = id,
        status = status,
        scoreRaw = scoreRaw,
        progress = progress,
        progressVolumes = 0,
        repeat = 0,
        isPrivate = false,
        notes = "",
        hiddenFromStatusLists = false,
        customLists = emptySet(),
        startedAt = FuzzyDate(2026, 1, 5),
        completedAt = null,
        updatedAt = Instant.parse("2026-09-28T12:00:00Z"),
        media = MediaLite(
            id = id,
            type = type,
            format = format,
            status = MediaStatus.FINISHED,
            episodes = if (type == MediaType.ANIME) total else null,
            chapters = if (type == MediaType.MANGA) total else null,
            volumes = null,
            title = MediaTitle(title, title, null, null),
            coverUrl = null,
            coverColor = color,
            year = year,
            averageScore = 90,
            nextAiringEpisode = null,
            isAdult = false
        )
    )

    val frieren =
        entry(154587, "Sousou no Frieren", MediaFormat.TV, 2023, MediaListStatus.CURRENT, 18, 28, 90, "#e4a15d")
    val apothecary =
        entry(161645, "Kusuriya no Hitorigoto", MediaFormat.TV, 2023, MediaListStatus.CURRENT, 23, 24, 0, "#5da1e4")
    val dandadan = entry(171018, "Dandadan", MediaFormat.TV, 2024, MediaListStatus.PLANNING, 0, 12, 0, "#e45d8b")
    val onePiece = entry(21, "ONE PIECE", MediaFormat.TV, 1999, MediaListStatus.CURRENT, 1120, null, 85, "#e4c75d")

    val all = listOf(frieren, apothecary, dandadan, onePiece)
}
