package com.tobfd.tsuzuki.core.data.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.enumNamed
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.BrowseSort
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SearchResult
import com.tobfd.tsuzuki.core.network.SearchMediaQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.type.MediaFormat as NetworkMediaFormat
import com.tobfd.tsuzuki.core.network.type.MediaSeason as NetworkMediaSeason
import com.tobfd.tsuzuki.core.network.type.MediaSort
import com.tobfd.tsuzuki.core.network.type.MediaStatus as NetworkMediaStatus
import com.tobfd.tsuzuki.core.network.type.MediaType as NetworkMediaType
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pages of `SearchMedia`, keyed by page number from 1 (docs/ANILIST_API.md, Pagination). Media a later
 * page repeats (AniList's order can shift between requests) are left out, so list keys stay unique.
 */
internal class SearchPagingSource(
    private val apolloClient: ApolloClient,
    private val query: BrowseQuery,
    private val isAdult: Optional<Boolean?>
) : PagingSource<Int, SearchResult>() {

    private val seen: MutableSet<Int> = Collections.synchronizedSet(mutableSetOf())

    /** Results handed out so far, for [BrowseQuery.limit]. */
    private val delivered = AtomicInteger()

    override fun getRefreshKey(state: PagingState<Int, SearchResult>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchResult> {
        val page = params.key ?: 1
        val response = try {
            apolloClient.query(query.toSearchMediaQuery(page, isAdult))
                .fetchPolicy(FetchPolicy.NetworkFirst)
                .execute()
        } catch (e: ApolloException) {
            return LoadResult.Error(e.toAppError())
        }
        val data = response.data?.Page
            ?: return LoadResult.Error(response.appErrorOrNull() ?: AppError.Unknown("AniList returned no results"))
        val limit = query.limit
        val kept = data.media.orEmpty()
            .mapNotNull { media ->
                val lite = media?.mediaCard?.toModel() ?: return@mapNotNull null
                SearchResult(lite, enumNamed<MediaListStatus>(media.mediaListEntry?.status?.rawValue))
            }
            .filter { seen.add(it.media.id) }
            .let { if (limit != null) it.take((limit - delivered.get()).coerceAtLeast(0)) else it }
        val total = delivered.addAndGet(kept.size)
        val more = data.pageInfo?.hasNextPage == true && (limit == null || total < limit)
        return LoadResult.Page(data = kept, prevKey = null, nextKey = if (more) page + 1 else null)
    }
}

/** The request for one page of [this] query. */
internal fun BrowseQuery.toSearchMediaQuery(page: Int, isAdult: Optional<Boolean?>): SearchMediaQuery {
    val year = filter.year
    val anime = type == MediaType.ANIME
    return SearchMediaQuery(
        page = page,
        type = NetworkMediaType.safeValueOf(type.name),
        search = Optional.presentIfNotNull(search),
        format_in = filter.formats.presentList { NetworkMediaFormat.safeValueOf(it.name) },
        status_in = filter.statuses.presentList { NetworkMediaStatus.safeValueOf(it.name) },
        season = Optional.presentIfNotNull(filter.season?.let { NetworkMediaSeason.safeValueOf(it.name) }),
        seasonYear = Optional.presentIfNotNull(year?.takeIf { anime }),
        // FuzzyDateInt YYYYMMDD; "greater" and "lesser" are exclusive.
        startDate_greater = Optional.presentIfNotNull(year?.takeIf { !anime }?.let { it * 10_000 }),
        startDate_lesser = Optional.presentIfNotNull(year?.takeIf { !anime }?.let { (it + 1) * 10_000 }),
        genre_in = filter.genres.presentList { it },
        tag_in = filter.tags.presentList { it },
        countryOfOrigin = Optional.presentIfNotNull(filter.countryOfOrigin),
        sort = Optional.present(sortFor(filter.sort, searching = search != null)),
        isAdult = isAdult
    )
}

private fun <T, R> Set<T>.presentList(transform: (T) -> R): Optional<List<R>?> =
    if (isEmpty()) Optional.Absent else Optional.present(map(transform))

/** No sort chosen: best match while searching (like anilist.co), else most popular. */
internal fun sortFor(sort: BrowseSort?, searching: Boolean): List<MediaSort> = when (sort) {
    null -> if (searching) listOf(MediaSort.SEARCH_MATCH) else listOf(MediaSort.POPULARITY_DESC)
    BrowseSort.Popularity -> listOf(MediaSort.POPULARITY_DESC)
    BrowseSort.Trending -> listOf(MediaSort.TRENDING_DESC, MediaSort.POPULARITY_DESC)
    BrowseSort.Score -> listOf(MediaSort.SCORE_DESC)
    BrowseSort.Newest -> listOf(MediaSort.START_DATE_DESC)
    BrowseSort.RecentlyAdded -> listOf(MediaSort.ID_DESC)
    BrowseSort.Favourites -> listOf(MediaSort.FAVOURITES_DESC)
    BrowseSort.Title -> listOf(MediaSort.TITLE_ROMAJI)
}
