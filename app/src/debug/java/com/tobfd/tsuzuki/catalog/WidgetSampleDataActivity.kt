package com.tobfd.tsuzuki.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import com.tobfd.tsuzuki.core.database.entity.FriendActivityEntity
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Debug only, for checking the widgets on an emulator without an AniList account: writes a made-up
 * session and sample list entries, airing times and friends' activities into the app's own storage
 * (`--es mode clear` removes them again). The token is not real, so keep the emulator offline while
 * using it: `adb shell cmd connectivity airplane-mode enable`, then
 * `adb shell am start -n com.tobfd.tsuzuki/.catalog.WidgetSampleDataActivity`.
 */
@AndroidEntryPoint
class WidgetSampleDataActivity : ComponentActivity() {
    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var database: TsuzukiDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runBlocking {
            if (intent.getStringExtra("mode") == "clear") {
                database.clearAllTables()
                sessionStore.clear()
            } else {
                seed(Instant.now())
            }
        }
        finish()
    }

    private suspend fun seed(now: Instant) {
        sessionStore.saveToken("debug-sample-token", now + Duration.ofDays(300))
        sessionStore.saveViewer(
            Viewer(
                id = 1,
                name = "sample",
                avatarUrl = null,
                options = ViewerOptions(
                    TitleLanguage.ROMAJI,
                    StaffNameLanguage.ROMAJI_WESTERN,
                    displayAdultContent = false,
                    scoreFormat = ScoreFormat.POINT_10_DECIMAL
                )
            ),
            now
        )
        val samples = listOf(
            Sample(1, 154587, "ANIME", "Sousou no Frieren", 18, 28, "FINISHED", null, null, "#e4a15d"),
            Sample(
                2, 182255, "ANIME", "Sousou no Frieren 2nd Season", 3, 12, "RELEASING", 6,
                now + Duration.ofHours(
                    5
                ),
                "#e4a15d"
            ),
            Sample(3, 21, "ANIME", "ONE PIECE", 1143, null, "RELEASING", 1146, now + Duration.ofDays(2), "#e4865d"),
            Sample(4, 30002, "MANGA", "Berserk", 364, null, "RELEASING", null, null, "#e4a143"),
            Sample(5, 170942, "ANIME", "Ao no Hako", 19, 25, "RELEASING", 20, now + Duration.ofMinutes(40), "#5da1e4")
        )
        val dao = database.mediaListDao()
        dao.upsertMedia(samples.map { it.media() })
        samples.forEachIndexed { index, sample -> dao.upsertEntry(sample.entry(now.epochSecond - index * 600)) }
        database.friendActivityDao().replaceAll(
            listOf(
                friend(1, "GeckoTV", now - Duration.ofMinutes(12), "watched episode", "5", samples[1], now),
                friend(2, "Sora", now - Duration.ofHours(3), "completed", null, samples[0], now),
                FriendActivityEntity(
                    id = 3, userId = 3, userName = "Mika", userAvatarUrl = null,
                    createdAt = (now - Duration.ofDays(2)).epochSecond, status = null, progress = null,
                    html = "Finally caught up! <span class='markdown_spoiler'>Himmel!</span> What a show.",
                    mediaId = null, mediaType = null, mediaTitle = null, coverUrl = null, coverColor = null,
                    fetchedAt = now.toEpochMilli()
                ),
                friend(4, "GeckoTV", now - Duration.ofDays(8), "read chapter", "120", samples[3], now)
            )
        )
    }

    private data class Sample(
        val entryId: Int,
        val mediaId: Int,
        val type: String,
        val title: String,
        val progress: Int,
        val total: Int?,
        val status: String,
        val nextEpisode: Int?,
        val nextAiringAt: Instant?,
        val color: String
    ) {
        fun media() = MediaLiteEntity(
            id = mediaId, type = type, format = if (type == "ANIME") "TV" else "MANGA", status = status,
            episodes = total.takeIf { type == "ANIME" }, chapters = total.takeIf { type == "MANGA" }, volumes = null,
            titleUserPreferred = title, titleRomaji = title, titleEnglish = null, titleNative = null, coverUrl = null,
            coverColor = color, year = 2026, averageScore = 85, nextAiringEpisode = nextEpisode, isAdult = false,
            nextAiringAt = nextAiringAt?.epochSecond
        )

        fun entry(updatedAt: Long) = MediaListEntryEntity(
            id = entryId, mediaId = mediaId, type = type, status = "CURRENT", scoreRaw = 0, progress = progress,
            progressVolumes = 0, repeat = 0, isPrivate = false, notes = "", hiddenFromStatusLists = false,
            customLists = "", startedYear = null, startedMonth = null, startedDay = null, completedYear = null,
            completedMonth = null, completedDay = null, updatedAt = updatedAt, syncRun = 1
        )
    }

    private fun friend(
        id: Int,
        name: String,
        at: Instant,
        status: String,
        progress: String?,
        media: Sample,
        now: Instant
    ) = FriendActivityEntity(
        id = id, userId = id, userName = name, userAvatarUrl = null, createdAt = at.epochSecond, status = status,
        progress = progress, html = null, mediaId = media.mediaId, mediaType = media.type,
        mediaTitle = media.title, coverUrl = null, coverColor = media.color, fetchedAt = now.toEpochMilli()
    )
}
