package com.tobfd.tsuzuki.feature.widgets

import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaTitle
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.feature.widgets.inprogress.InProgressItem
import com.tobfd.tsuzuki.feature.widgets.nextepisode.UpcomingItem
import java.time.Duration
import java.time.Instant

/**
 * What the widget picker shows (`providePreview`, Android 15+): made-up progress on well-known titles,
 * with AniList's cover colors instead of cover images, so no user data and no image is bundled.
 */
internal object WidgetPreviewData {
    val inProgress = listOf(
        InProgressItem(1, 154587, MediaType.ANIME, "Sousou no Frieren", 18, 28, null, "#e4a15d"),
        InProgressItem(2, 30002, MediaType.MANGA, "Berserk", 364, null, null, "#e4a143"),
        InProgressItem(3, 21, MediaType.ANIME, "ONE PIECE", 1071, null, null, "#e4865d")
    )

    fun upcoming(now: Instant) = listOf(
        UpcomingItem(
            182255,
            "Sousou no Frieren 2nd Season",
            6,
            now + Duration.ofHours(5).plusMinutes(7),
            5,
            null,
            "#e4a15d"
        ),
        UpcomingItem(21, "ONE PIECE", 1146, now + Duration.ofDays(2), 1143, null, "#e4865d"),
        UpcomingItem(170942, "Blue Box", 20, now + Duration.ofDays(4), 19, null, "#5da1e4")
    )

    fun friendActivity(now: Instant): List<Activity> {
        val gecko = UserLite(1, "GeckoTV", null)
        val sora = UserLite(2, "Sora", null)
        return listOf(
            listUpdate(
                1,
                gecko,
                now - Duration.ofMinutes(12),
                "watched episode",
                "5",
                154587,
                "Sousou no Frieren",
                "#e4a15d"
            ),
            listUpdate(2, sora, now - Duration.ofHours(2), "completed", null, 16498, "Shingeki no Kyojin", "#e4ae5d"),
            listUpdate(3, gecko, now - Duration.ofHours(5), "read chapter", "120", 30002, "Berserk", "#e4a143")
        )
    }

    private fun listUpdate(
        id: Int,
        user: UserLite,
        createdAt: Instant,
        status: String,
        progress: String?,
        mediaId: Int,
        title: String,
        coverColor: String
    ) = Activity.ListUpdate(
        id = id,
        user = user,
        createdAt = createdAt,
        likeCount = 0,
        isLiked = false,
        replyCount = 0,
        siteUrl = null,
        status = status,
        progress = progress,
        media = MediaLite(
            id = mediaId,
            type = if (status == "read chapter") MediaType.MANGA else MediaType.ANIME,
            format = null,
            status = null,
            episodes = null,
            chapters = null,
            volumes = null,
            title = MediaTitle(title, title, null, null),
            coverUrl = null,
            coverColor = coverColor,
            year = null,
            averageScore = null,
            nextAiringEpisode = null,
            isAdult = false
        )
    )
}
