package com.tobfd.tsuzuki.core.data.home

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.ActivityFeedQuery
import com.tobfd.tsuzuki.core.network.HomeQuery
import com.tobfd.tsuzuki.core.network.ToggleActivityLikeMutation
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Whose activities the Home feed shows. */
enum class FeedScope {
    Following,
    Global
}

/** One page of the feed; [fromCache] is set when it came from the Apollo cache, not the network. */
data class FeedPage(val activities: List<Activity>, val hasNextPage: Boolean, val fromCache: Boolean)

/**
 * Home's network data (docs/ROADMAP.md, M5): the trending row and the activity feed. Trending and the
 * feed's first page come from one `Home` request; later pages from `ActivityFeed`. "In Progress" and
 * "Up next" come from Room through `ListRepository`.
 */
interface HomeRepository {
    /** Trending anime, filled by every first feed page (network or cache). */
    val trending: StateFlow<List<MediaLite>>

    /**
     * One page of the feed, [FEED_PAGE_SIZE] activities (docs/ANILIST_API.md, Pagination). Page 1
     * comes with the trending row in one `Home` request. With [cacheOnly] it is read from the Apollo
     * cache without a request and fails with [AppError.NotFound] when it is not there.
     */
    suspend fun feedPage(scope: FeedScope, page: Int, cacheOnly: Boolean): Result<FeedPage>

    /** Likes or unlikes an activity; returns whether it is liked now. */
    suspend fun toggleLike(activityId: Int): Result<Boolean>
}

/** Activities per feed page; the `perPage` of both feed queries. */
const val FEED_PAGE_SIZE = 25

@Singleton
internal class DefaultHomeRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val sessionRepository: SessionRepository
) : HomeRepository {

    private val trendingState = MutableStateFlow<List<MediaLite>>(emptyList())
    override val trending: StateFlow<List<MediaLite>> = trendingState.asStateFlow()

    override suspend fun feedPage(scope: FeedScope, page: Int, cacheOnly: Boolean): Result<FeedPage> {
        val following = scope == FeedScope.Following
        return if (page == 1) {
            firstPage(following, cacheOnly)
        } else {
            val query = ActivityFeedQuery(
                page = page,
                isFollowing = Optional.present(following),
                hasRepliesOrTypeText = if (following) Optional.Absent else Optional.present(true)
            )
            val response = try {
                apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkFirst).execute()
            } catch (e: ApolloException) {
                return Result.failure(e.toAppError())
            }
            response.appErrorOrNull()?.let { return Result.failure(it) }
            val data = response.data?.Page ?: return Result.failure(AppError.Unknown("AniList returned no feed"))
            Result.success(
                FeedPage(
                    activities = data.activities.orEmpty().mapNotNull { it?.feedActivity?.toModel() },
                    hasNextPage = data.pageInfo?.hasNextPage == true,
                    fromCache = false
                )
            )
        }
    }

    private suspend fun firstPage(following: Boolean, cacheOnly: Boolean): Result<FeedPage> {
        val adult =
            (sessionRepository.session.first() as? SessionState.LoggedIn)?.viewer?.options?.displayAdultContent == true
        val query = HomeQuery(
            isFollowing = Optional.present(following),
            hasRepliesOrTypeText = if (following) Optional.Absent else Optional.present(true),
            isAdult = Optional.present(adult)
        )
        val response = try {
            apolloClient.query(query)
                .fetchPolicy(if (cacheOnly) FetchPolicy.CacheOnly else FetchPolicy.NetworkFirst)
                .execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        // A cache miss is not an error here; the caller goes to the network.
        if (cacheOnly && response.data == null) return Result.failure(AppError.NotFound)
        response.appErrorOrNull()?.let { if (response.data == null) return Result.failure(it) }
        val data = response.data ?: return Result.failure(AppError.Unknown("AniList returned no feed"))
        data.trending?.media?.mapNotNull { it?.mediaCard?.toModel() }?.let { trendingState.value = it }
        return Result.success(
            FeedPage(
                activities = data.feed?.activities.orEmpty().mapNotNull { it?.feedActivity?.toModel() },
                hasNextPage = data.feed?.pageInfo?.hasNextPage == true,
                fromCache = cacheOnly
            )
        )
    }

    override suspend fun toggleLike(activityId: Int): Result<Boolean> {
        val response = try {
            apolloClient.mutation(ToggleActivityLikeMutation(activityId)).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        val liked = response.data?.ToggleLikeV2?.let {
            it.onListActivity?.isLiked ?: it.onTextActivity?.isLiked ?: it.onMessageActivity?.isLiked
        } ?: return Result.failure(AppError.Unknown("AniList returned no like"))
        return Result.success(liked)
    }
}
