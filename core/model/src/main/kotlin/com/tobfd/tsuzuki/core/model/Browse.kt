package com.tobfd.tsuzuki.core.model

import java.time.LocalDate

/** Sort orders of the Browse filter sheet; each maps to one AniList `MediaSort`. */
enum class BrowseSort {
    Popularity,
    Trending,
    Score,
    Newest,
    RecentlyAdded,
    Favourites,
    Title
}

/** The formats AniList uses for each media type, in the order the filter sheet shows them. */
fun MediaType.formats(): List<MediaFormat> = when (this) {
    MediaType.ANIME -> listOf(
        MediaFormat.TV,
        MediaFormat.TV_SHORT,
        MediaFormat.MOVIE,
        MediaFormat.SPECIAL,
        MediaFormat.OVA,
        MediaFormat.ONA,
        MediaFormat.MUSIC
    )

    MediaType.MANGA -> listOf(MediaFormat.MANGA, MediaFormat.NOVEL, MediaFormat.ONE_SHOT)
}

/** The filter sheet's choices (docs/DESIGN.md, Browse). Empty sets mean "any". */
data class BrowseFilter(
    val formats: Set<MediaFormat> = emptySet(),
    val statuses: Set<MediaStatus> = emptySet(),
    /** Anime only. */
    val season: MediaSeason? = null,
    val year: Int? = null,
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    /** ISO 3166 code, e.g. "KR" for manhwa; set by the quick chips only. */
    val countryOfOrigin: String? = null,
    /** Null: best match while searching, else most popular. */
    val sort: BrowseSort? = null
) {
    /** How many choices differ from the default: the badge on the filter button. */
    val activeCount: Int
        get() = listOf(
            formats.isNotEmpty(),
            statuses.isNotEmpty(),
            season != null,
            year != null,
            genres.isNotEmpty(),
            tags.isNotEmpty(),
            countryOfOrigin != null,
            sort != null
        ).count { it }

    val isActive: Boolean
        get() = activeCount > 0

    /** Drops what [type] can't have: other formats, and the season for manga. */
    fun forType(type: MediaType): BrowseFilter = copy(
        formats = formats.filterTo(mutableSetOf()) { it in type.formats() },
        season = season.takeIf { type == MediaType.ANIME }
    )
}

/** One search: what the result list asks AniList for. */
data class BrowseQuery(
    val type: MediaType,
    /** Null or at least 2 characters (docs/ANILIST_API.md, Rate limit). */
    val search: String?,
    val filter: BrowseFilter,
    /** Stops paging after this many results ("Top 100"); null pages until AniList has no more. */
    val limit: Int? = null
)

/** The quick chips under the search field: preset filters (graphql `SearchMedia` comment). */
enum class QuickFilter {
    Trending,
    Top100,
    ThisSeason,
    TopMovies,
    TopManhwa;

    /** The media type this chip needs, or null when it works for both. */
    val type: MediaType?
        get() = when (this) {
            Trending, Top100 -> null
            ThisSeason, TopMovies -> MediaType.ANIME
            TopManhwa -> MediaType.MANGA
        }

    /** The filter this chip stands for, with "this season" taken from [today]. */
    fun filter(today: LocalDate): BrowseFilter = when (this) {
        Trending -> BrowseFilter(sort = BrowseSort.Trending)

        Top100 -> BrowseFilter(sort = BrowseSort.Score)

        ThisSeason -> MediaSeason.of(today).let { (season, year) ->
            BrowseFilter(season = season, year = year, sort = BrowseSort.Popularity)
        }

        TopMovies -> BrowseFilter(formats = setOf(MediaFormat.MOVIE), sort = BrowseSort.Score)

        TopManhwa -> BrowseFilter(countryOfOrigin = "KR", sort = BrowseSort.Score)
    }

    /** Results shown before paging stops. */
    val limit: Int?
        get() = if (this == Top100) TOP_LIMIT else null

    private companion object {
        const val TOP_LIMIT = 100
    }
}

/** A search result; [listStatus] is the viewer's list status, null for guests or when not on the list. */
data class SearchResult(val media: MediaLite, val listStatus: MediaListStatus?)

/** The idle Browse page: two rows from one `BrowseHome` request. */
data class BrowseHome(val trending: List<MediaLite>, val newlyAdded: List<MediaLite>)

/** A tag for the filter sheet. */
data class TagOption(val name: String, val category: String?, val isAdult: Boolean)

/** Genres and tags the filter sheet offers, from `GenresAndTags`. */
data class FilterOptions(val genres: List<String>, val tags: List<TagOption>)
