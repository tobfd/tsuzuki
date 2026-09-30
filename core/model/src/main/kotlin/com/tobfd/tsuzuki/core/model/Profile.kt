package com.tobfd.tsuzuki.core.model

import java.time.LocalDate

/** Everything a profile shows (docs/DESIGN.md, Profile), from one `UserProfile` request. */
data class UserProfile(
    val id: Int,
    val name: String,
    /** AniList's HTML subset; null when the user wrote nothing. */
    val aboutHtml: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val siteUrl: String?,
    /** The viewer follows this user. */
    val isFollowing: Boolean,
    /** This user follows the viewer. */
    val isFollower: Boolean,
    val anime: ListStatistics,
    val manga: ListStatistics,
    /** Days with activity, oldest first; days without activity are missing. */
    val activityHistory: List<ActivityDay>,
    val favourites: Favourites,
    /** The newest activities of this user. */
    val recentActivity: List<Activity>
)

/**
 * Totals of one list. [progress] is episodes watched (anime) or chapters read (manga); [minutesWatched]
 * is 0 for manga. [meanScore] is as AniList sends it.
 */
data class ListStatistics(
    val count: Int,
    val progress: Int,
    val volumes: Int,
    val minutesWatched: Int,
    val meanScore: Double,
    val standardDeviation: Double,
    val statuses: Map<MediaListStatus, Int>
) {
    val daysWatched: Double
        get() = minutesWatched / MINUTES_PER_DAY

    companion object {
        private const val MINUTES_PER_DAY = 1_440.0

        val Empty = ListStatistics(0, 0, 0, 0, 0.0, 0.0, emptyMap())
    }
}

/** One day of the activity history. */
data class ActivityDay(val date: LocalDate, val amount: Int)

data class Favourites(
    val anime: List<MediaLite>,
    val manga: List<MediaLite>,
    val characters: List<PersonLite>,
    val staff: List<PersonLite>
) {
    val isEmpty: Boolean
        get() = anime.isEmpty() && manga.isEmpty() && characters.isEmpty() && staff.isEmpty()
}

/** A row of the Social tab; [followsViewer] is AniList's `isFollower`. */
data class FollowUser(val user: UserLite, val followsViewer: Boolean)
