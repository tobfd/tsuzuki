package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.home.FeedPage
import com.tobfd.tsuzuki.core.data.home.FeedScope
import com.tobfd.tsuzuki.core.data.home.HomeRepository
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaLite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [HomeRepository]; records every feed page asked for and every like. */
class FakeHomeRepository(trending: List<MediaLite> = emptyList(), activities: List<Activity> = emptyList()) :
    HomeRepository {

    /** One call of [feedPage]. */
    data class PageRequest(val scope: FeedScope, val page: Int, val cacheOnly: Boolean)

    override val trending: StateFlow<List<MediaLite>> = MutableStateFlow(trending)

    /** The feed's pages by number, the same for both scopes; a page not set here is empty. */
    val pages = mutableMapOf(1 to activities)

    /** Whether the first page is in the "cache", so a cache-only request finds it. */
    var cached = false

    /** When set, network requests fail with it. */
    var failure: AppError? = null

    /** When set, network requests wait for it, so tests can look at the loading state. */
    var pageGate: CompletableDeferred<Unit>? = null

    val pageRequests = mutableListOf<PageRequest>()

    var likeResult: Result<Boolean>? = null

    /** When set, [toggleLike] waits for it, so tests can look at the optimistic state. */
    var likeGate: CompletableDeferred<Unit>? = null
    val likedIds = mutableListOf<Int>()

    override suspend fun feedPage(scope: FeedScope, page: Int, cacheOnly: Boolean): Result<FeedPage> {
        pageRequests += PageRequest(scope, page, cacheOnly)
        if (cacheOnly) {
            if (!cached || page != 1) return Result.failure(AppError.NotFound)
        } else {
            pageGate?.await()
            failure?.let { return Result.failure(it) }
        }
        return Result.success(
            FeedPage(
                activities = pages[page].orEmpty(),
                hasNextPage = pages.keys.any { it > page },
                fromCache = cacheOnly
            )
        )
    }

    override suspend fun toggleLike(activityId: Int): Result<Boolean> {
        likedIds += activityId
        likeGate?.await()
        return likeResult ?: Result.success(true)
    }
}
