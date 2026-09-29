package com.tobfd.tsuzuki.core.testing

import androidx.paging.PagingData
import com.tobfd.tsuzuki.core.data.home.FeedPage
import com.tobfd.tsuzuki.core.data.home.FeedScope
import com.tobfd.tsuzuki.core.data.home.HomeRepository
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaLite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** In-memory [HomeRepository]; records which feeds were asked for and every like. */
class FakeHomeRepository(trending: List<MediaLite> = emptyList(), var activities: List<Activity> = emptyList()) :
    HomeRepository {

    override val trending: StateFlow<List<MediaLite>> = MutableStateFlow(trending)

    /** (scope, firstPageFromCache) of every feed that was created. */
    val feedRequests = mutableListOf<Pair<FeedScope, Boolean>>()

    /** When set, the next feed reports that its first page came from the cache. */
    var firstPageFromCache = false

    var likeResult: Result<Boolean>? = null

    /** When set, [toggleLike] waits for it, so tests can look at the optimistic state. */
    var likeGate: CompletableDeferred<Unit>? = null
    val likedIds = mutableListOf<Int>()

    override fun feed(
        scope: FeedScope,
        firstPageFromCache: Boolean,
        onFirstPage: (fromCache: Boolean) -> Unit
    ): Flow<PagingData<Activity>> {
        feedRequests += scope to firstPageFromCache
        onFirstPage(firstPageFromCache && this.firstPageFromCache)
        return flowOf(PagingData.from(activities))
    }

    override suspend fun feedPage(scope: FeedScope, page: Int, cacheOnly: Boolean): Result<FeedPage> =
        Result.success(FeedPage(activities, hasNextPage = false, fromCache = cacheOnly))

    override suspend fun toggleLike(activityId: Int): Result<Boolean> {
        likedIds += activityId
        likeGate?.await()
        return likeResult ?: Result.success(true)
    }
}
