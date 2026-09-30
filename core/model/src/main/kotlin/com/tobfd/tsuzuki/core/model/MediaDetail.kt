package com.tobfd.tsuzuki.core.model

/** Everything the detail page shows (docs/DESIGN.md, Media detail), from one `MediaDetail` request. */
data class MediaDetail(
    val media: MediaLite,
    val bannerUrl: String?,
    val coverUrl: String?,
    /** AniList's small HTML subset, spoilers included. */
    val descriptionHtml: String?,
    val genres: List<String>,
    val tags: List<MediaTag>,
    val info: MediaInfo,
    val isFavourite: Boolean,
    val siteUrl: String?,
    val rankings: List<MediaRanking>,
    val streamingLinks: List<StreamingLink>,
    val trailer: Trailer?,
    val relations: List<MediaRelation>,
    val characters: List<CharacterRole>,
    val staff: List<StaffRole>,
    val statusDistribution: Map<MediaListStatus, Int>,
    /** Score (10, 20, … 100) to number of users. */
    val scoreDistribution: List<Pair<Int, Int>>,
    val recommendations: List<Recommendation>,
    /** What the people the viewer follows think of it; empty for guests. */
    val following: List<FollowingEntry>
)

data class MediaTag(val name: String, val rank: Int?, val isSpoiler: Boolean)

/** The facts in the info grid. Season and source are AniList's names, e.g. `FALL`, `LIGHT_NOVEL`. */
data class MediaInfo(
    val episodeDuration: Int?,
    val season: String?,
    val source: String?,
    val startDate: FuzzyDate?,
    val endDate: FuzzyDate?,
    val studios: List<String>,
    val popularity: Int?,
    val favourites: Int?,
    val meanScore: Int?
)

/** A place in AniList's charts. */
data class MediaRanking(val rank: Int, val type: RankingType, val allTime: Boolean, val year: Int?, val season: String?)

enum class RankingType {
    Rated,
    Popular
}

/** The ranking the header shows: best rated of all time, else of its year, else most popular of all time. */
fun List<MediaRanking>.headline(): MediaRanking? = firstOrNull { it.type == RankingType.Rated && it.allTime }
    ?: firstOrNull { it.type == RankingType.Rated && it.year != null && it.season == null }
    ?: firstOrNull { it.type == RankingType.Popular && it.allTime }

data class StreamingLink(val site: String, val url: String, val color: String?)

data class Trailer(val site: String, val id: String) {
    /** Opens in the YouTube app when installed; other sites have no link. */
    val url: String?
        get() = if (site.equals("youtube", ignoreCase = true)) "https://www.youtube.com/watch?v=$id" else null
}

/** A related media; [type] is AniList's `MediaRelation`, e.g. `SEQUEL`, `ADAPTATION`. */
data class MediaRelation(val type: String, val media: MediaLite)

data class PersonLite(val id: Int, val name: String, val imageUrl: String?)

/** A character with their role (`MAIN`, `SUPPORTING`, `BACKGROUND`) and Japanese voice actor. */
data class CharacterRole(val character: PersonLite, val role: String?, val voiceActor: PersonLite?)

data class StaffRole(val staff: PersonLite, val role: String?)

data class Recommendation(val rating: Int, val media: MediaLite)

/** [score] is in the viewer's score format, as AniList sends it; 0 is not scored. */
data class FollowingEntry(val user: UserLite, val status: MediaListStatus, val score: Double, val progress: Int)
