package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaFormat
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaTitle
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import java.time.Instant

/** Sample data shaped like real AniList responses. */
object SampleData {
    val viewer = Viewer(
        id = 5_424_000,
        name = "tobfd",
        avatarUrl = null,
        options = ViewerOptions(
            titleLanguage = TitleLanguage.ROMAJI,
            staffNameLanguage = StaffNameLanguage.ROMAJI_WESTERN,
            displayAdultContent = false,
            scoreFormat = ScoreFormat.POINT_10_DECIMAL
        )
    )

    val tokenExpiry: Instant = Instant.parse("2027-09-28T12:00:00Z")

    /** Frieren (AniList 154587): 28 episodes, the viewer is at 18. */
    val frieren: MediaListEntry = listEntry(
        id = 400_000_001,
        mediaId = 154_587,
        title = "Sousou no Frieren",
        englishTitle = "Frieren: Beyond Journey's End",
        progress = 18,
        total = 28,
        scoreRaw = 90
    )

    /** A list entry with sensible defaults; [total] is episodes for anime and chapters for manga. */
    fun listEntry(
        id: Int,
        mediaId: Int = id,
        title: String = "Media $mediaId",
        englishTitle: String? = null,
        type: MediaType = MediaType.ANIME,
        status: MediaListStatus = MediaListStatus.CURRENT,
        progress: Int = 0,
        total: Int? = 12,
        scoreRaw: Int = 0,
        customLists: Set<String> = emptySet(),
        hiddenFromStatusLists: Boolean = false,
        updatedAt: Instant = Instant.parse("2026-09-28T12:00:00Z"),
        startedAt: FuzzyDate? = null
    ) = MediaListEntry(
        id = id,
        mediaId = mediaId,
        status = status,
        scoreRaw = scoreRaw,
        progress = progress,
        progressVolumes = 0,
        repeat = 0,
        isPrivate = false,
        notes = "",
        hiddenFromStatusLists = hiddenFromStatusLists,
        customLists = customLists,
        startedAt = startedAt,
        completedAt = null,
        updatedAt = updatedAt,
        media = MediaLite(
            id = mediaId,
            type = type,
            format = if (type == MediaType.ANIME) MediaFormat.TV else MediaFormat.MANGA,
            status = MediaStatus.FINISHED,
            episodes = if (type == MediaType.ANIME) total else null,
            chapters = if (type == MediaType.MANGA) total else null,
            volumes = null,
            title = MediaTitle(title, title, englishTitle, null),
            coverUrl = null,
            coverColor = "#e4a15d",
            year = 2023,
            averageScore = 91,
            nextAiringEpisode = null,
            isAdult = false
        )
    )
}
