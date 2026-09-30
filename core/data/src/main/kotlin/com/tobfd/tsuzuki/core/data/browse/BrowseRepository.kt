package com.tobfd.tsuzuki.core.data.browse

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.isFromCache
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.session.isAdultArgument
import com.tobfd.tsuzuki.core.model.BrowseHome
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.FilterOptions
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SearchResult
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.TagOption
import com.tobfd.tsuzuki.core.network.BrowseHomeQuery
import com.tobfd.tsuzuki.core.network.GenresAndTagsQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.type.MediaType as NetworkMediaType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** How long the idle rows count as fresh (docs/ANILIST_API.md, Caching). */
private val HOME_MAX_AGE: Duration = Duration.ofMinutes(30)

/** Results per search page; the `perPage` of `SearchMedia`. */
const val SEARCH_PAGE_SIZE = 20

/** Loads the next page when the list is this close to its end. */
private const val PREFETCH_DISTANCE = 5

/** The Browse tab (docs/ROADMAP.md, M7): idle rows, search with filters, filter options. */
interface BrowseRepository {
    /** Trending and newly added of [type] in one `BrowseHome` request, from the cache for 30 minutes. */
    suspend fun home(type: MediaType): Result<BrowseHome>

    /**
     * Results for [query], [SEARCH_PAGE_SIZE] per request. The next page loads only while AniList says
     * `hasNextPage` and, for "Top 100", until the limit. Adult media stay hidden unless the viewer
     * turned them on.
     */
    fun search(query: BrowseQuery): Flow<PagingData<SearchResult>>

    /** Genres and tags for the filter sheet: one request per app run, adult tags only when allowed. */
    suspend fun filterOptions(): Result<FilterOptions>
}

@Singleton
internal class DefaultBrowseRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val sessionRepository: SessionRepository,
    private val clock: Clock
) : BrowseRepository {

    private val homeFetchedAt = ConcurrentHashMap<MediaType, Instant>()

    @Volatile
    private var options: FilterOptions? = null

    override suspend fun home(type: MediaType): Result<BrowseHome> {
        val last = homeFetchedAt[type]
        val fresh = last != null && Duration.between(last, clock.instant()) < HOME_MAX_AGE
        val query = BrowseHomeQuery(
            type = NetworkMediaType.safeValueOf(type.name),
            isAdult = sessionRepository.isAdultArgument()
        )
        val response = try {
            apolloClient.query(query)
                .fetchPolicy(if (fresh) FetchPolicy.CacheFirst else FetchPolicy.NetworkFirst)
                .execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        val data = response.data
            ?: return Result.failure(response.appErrorOrNull() ?: AppError.Unknown("AniList returned no rows"))
        if (!response.isFromCache) homeFetchedAt[type] = clock.instant()
        return Result.success(
            BrowseHome(
                trending = data.trending?.media.orEmpty().mapNotNull { it?.mediaCard?.toModel() },
                newlyAdded = data.newlyAdded?.media.orEmpty().mapNotNull { it?.mediaCard?.toModel() }
            )
        )
    }

    override fun search(query: BrowseQuery): Flow<PagingData<SearchResult>> = flow {
        val isAdult = sessionRepository.isAdultArgument()
        val pager = Pager(
            config = PagingConfig(
                pageSize = SEARCH_PAGE_SIZE,
                prefetchDistance = PREFETCH_DISTANCE,
                enablePlaceholders = false,
                initialLoadSize = SEARCH_PAGE_SIZE
            ),
            pagingSourceFactory = { SearchPagingSource(apolloClient, query, isAdult) }
        )
        emitAll(pager.flow)
    }

    override suspend fun filterOptions(): Result<FilterOptions> {
        val loaded = options ?: run {
            val response = try {
                apolloClient.query(GenresAndTagsQuery()).fetchPolicy(FetchPolicy.NetworkFirst).execute()
            } catch (e: ApolloException) {
                return Result.failure(e.toAppError())
            }
            val data = response.data
                ?: return Result.failure(response.appErrorOrNull() ?: AppError.Unknown("AniList returned no genres"))
            FilterOptions(
                genres = data.GenreCollection.orEmpty().filterNotNull(),
                tags = data.MediaTagCollection.orEmpty().mapNotNull { tag ->
                    tag?.let { TagOption(it.name, it.category, isAdult = it.isAdult == true) }
                }.sortedBy { it.name.lowercase() }
            ).also { if (!response.isFromCache) options = it }
        }
        val adultAllowed = (sessionRepository.session.first() as? SessionState.LoggedIn)
            ?.viewer?.options?.displayAdultContent == true
        return Result.success(if (adultAllowed) loaded else loaded.copy(tags = loaded.tags.filterNot { it.isAdult }))
    }
}
