package com.tobfd.tsuzuki.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.home.FeedPage
import com.tobfd.tsuzuki.core.data.home.FeedScope
import com.tobfd.tsuzuki.core.data.home.HomeRepository
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Up next from Planning" shows at most this many entries. */
internal const val UP_NEXT_COUNT = 3

/** Watching and rewatching entries of both lists, the most recently updated first. */
internal fun inProgressOf(entries: List<MediaListEntry>): List<MediaListEntry> = entries
    .filter { it.status == MediaListStatus.CURRENT || it.status == MediaListStatus.REPEATING }
    .sortedByDescending { it.updatedAt }

/**
 * Planned entries that can be started now (released or airing) first, then the rest, each group
 * the most recently changed first. Room keeps AniList's `updatedAt`, which for planned entries is
 * usually when they were added.
 */
internal fun upNextOf(entries: List<MediaListEntry>): List<MediaListEntry> = entries
    .filter { it.status == MediaListStatus.PLANNING }
    .sortedWith(
        compareBy<MediaListEntry> {
            it.media.status != MediaStatus.RELEASING && it.media.status != MediaStatus.FINISHED
        }
            .thenByDescending { it.updatedAt }
    )
    .take(UP_NEXT_COUNT)

/** A like the user just tapped, shown before AniList confirms it. */
data class LikeState(val isLiked: Boolean, val likeCount: Int)

/**
 * The activity feed, loaded a page (25) at a time: the first page on its own, every further page
 * when the user asks for it with "Load more".
 */
data class FeedUiState(
    val activities: ImmutableList<Activity> = persistentListOf(),
    /** The first page is loading and nothing is shown yet. */
    val isLoading: Boolean = true,
    /** Pull to refresh while activities are shown. */
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** The first page failed and there is nothing to show. */
    val error: AppError? = null
)

data class HomeUiState(
    val isGuest: Boolean = false,
    val inProgress: ImmutableList<MediaListEntry> = persistentListOf(),
    val upNext: ImmutableList<MediaListEntry> = persistentListOf(),
    val trending: ImmutableList<MediaLite> = persistentListOf(),
    val feedScope: FeedScope = FeedScope.Following,
    val feed: FeedUiState = FeedUiState(),
    val likes: ImmutableMap<Int, LikeState> = persistentMapOf()
)

sealed interface HomeEvent {
    /** A +1 finished the entry; the snackbar offers Undo, which restores [before]. */
    data class Completed(val before: MediaListEntry) : HomeEvent

    data class LikeFailed(val error: AppError) : HomeEvent

    /** Refreshing the feed or loading more failed while activities are shown. */
    data class FeedFailed(val error: AppError) : HomeEvent
}

private const val KEY_SCOPE = "feedScope"

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val homeRepository: HomeRepository,
    private val listRepository: ListRepository,
    sessionRepository: SessionRepository
) : ViewModel() {

    private val isGuest = sessionRepository.session.map { it !is SessionState.LoggedIn }.distinctUntilChanged()

    /** Guests only have the global feed. */
    private val scope = combine(savedState.getStateFlow(KEY_SCOPE, FeedScope.Following.name), isGuest) { name, guest ->
        if (guest) FeedScope.Global else FeedScope.entries.firstOrNull { it.name == name } ?: FeedScope.Following
    }.distinctUntilChanged()

    private val likes = MutableStateFlow<Map<Int, LikeState>>(emptyMap())

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow: Flow<HomeEvent> = events.receiveAsFlow()

    private val feed = MutableStateFlow(FeedUiState())
    private var feedScope = FeedScope.Following
    private var feedPages = 0

    /** The one feed request running; a new first page cancels it, "Load more" waits for it. */
    private var feedJob: Job? = null

    private val lists =
        combine(listRepository.observeList(MediaType.ANIME), listRepository.observeList(MediaType.MANGA)) {
                anime,
                manga
            ->
            anime.entries + manga.entries
        }

    private val feedAndLikes = combine(feed, likes, ::Pair)

    val uiState: StateFlow<HomeUiState> = combine(isGuest, lists, homeRepository.trending, scope, feedAndLikes) {
            guest,
            entries,
            trending,
            scope,
            (feed, likes)
        ->
        HomeUiState(
            isGuest = guest,
            inProgress = inProgressOf(entries).toImmutableList(),
            upNext = upNextOf(entries).toImmutableList(),
            trending = trending.toImmutableList(),
            feedScope = scope,
            feed = feed,
            likes = likes.toPersistentMap()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            scope.collect { scope -> loadFirstPage(scope, fromCache = true) }
        }
    }

    /**
     * Loads the feed's first page again. With [fromCache] (opening Home, another scope) the feed
     * starts over and shows the Apollo cache at once when it is there; then the network (one request:
     * feed page and trending together). Later pages are dropped.
     */
    private fun loadFirstPage(scope: FeedScope, fromCache: Boolean) {
        feedJob?.cancel()
        feedScope = scope
        if (fromCache) {
            feed.value = FeedUiState()
            feedPages = 0
        } else {
            feed.update {
                it.copy(
                    isLoading = it.activities.isEmpty(),
                    isRefreshing = it.activities.isNotEmpty(),
                    isLoadingMore = false,
                    error = null
                )
            }
        }
        feedJob = viewModelScope.launch {
            if (fromCache) homeRepository.feedPage(scope, page = 1, cacheOnly = true).onSuccess(::showFirstPage)
            homeRepository.feedPage(scope, page = 1, cacheOnly = false)
                .onSuccess(::showFirstPage)
                .onFailure { feedFailed(it) }
        }
    }

    private fun showFirstPage(page: FeedPage) {
        feedPages = 1
        feed.value = FeedUiState(
            activities = page.activities.distinctBy { it.id }.toImmutableList(),
            isLoading = false,
            hasMore = page.hasNextPage
        )
    }

    private suspend fun feedFailed(error: Throwable) {
        val appError = error as? AppError ?: AppError.Unknown(error.message)
        val shown = feed.value.activities.isNotEmpty()
        feed.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = appError.takeUnless { shown }
            )
        }
        if (shown) events.send(HomeEvent.FeedFailed(appError))
    }

    /** "Load more": the next page, added below. */
    fun onLoadMore() {
        val current = feed.value
        if (current.isLoadingMore || !current.hasMore) return
        feed.update { it.copy(isLoadingMore = true) }
        val running = feedJob
        feedJob = viewModelScope.launch {
            // A first page still on its way (the network after the cache) comes first.
            running?.join()
            if (!feed.value.hasMore) {
                feed.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            homeRepository.feedPage(feedScope, page = feedPages + 1, cacheOnly = false)
                .onSuccess { page ->
                    feedPages++
                    feed.update {
                        it.copy(
                            activities = (it.activities + page.activities)
                                .distinctBy { activity -> activity.id }
                                .toImmutableList(),
                            isLoadingMore = false,
                            hasMore = page.hasNextPage
                        )
                    }
                }
                .onFailure { feedFailed(it) }
        }
    }

    /** Retry after the first page failed. */
    fun onRetryFeed() {
        loadFirstPage(feedScope, fromCache = false)
    }

    fun onFeedScopeSelected(scope: FeedScope) {
        savedState[KEY_SCOPE] = scope.name
    }

    fun onPlusOne(entryId: Int) {
        viewModelScope.launch {
            val outcome = listRepository.plusOne(entryId) ?: return@launch
            if (outcome.completed) events.send(HomeEvent.Completed(outcome.before))
        }
    }

    fun onStart(entryId: Int) {
        viewModelScope.launch { listRepository.start(entryId) }
    }

    fun onUndo(before: MediaListEntry) {
        viewModelScope.launch { listRepository.restore(before) }
    }

    /** Pull to refresh: the lists (15-minute rule does not apply) and the feed from the network. */
    fun onRefresh() {
        loadFirstPage(feedScope, fromCache = false)
        viewModelScope.launch { listRepository.refresh(force = true) }
    }

    /** Optimistic: the heart changes at once and goes back if AniList says no. */
    fun onToggleLike(activity: Activity) {
        val current = likes.value[activity.id] ?: LikeState(activity.isLiked, activity.likeCount)
        val next = LikeState(!current.isLiked, (current.likeCount + if (current.isLiked) -1 else 1).coerceAtLeast(0))
        likes.update { it + (activity.id to next) }
        viewModelScope.launch {
            homeRepository.toggleLike(activity.id)
                .onSuccess { liked -> if (liked != next.isLiked) likes.update { it + (activity.id to current) } }
                .onFailure { error ->
                    likes.update { it + (activity.id to current) }
                    events.send(HomeEvent.LikeFailed(error as? AppError ?: AppError.Unknown(error.message)))
                }
        }
    }
}

/** [activity] with the like the user just tapped. */
fun Activity.withLike(like: LikeState?): Activity = if (like == null) {
    this
} else {
    when (this) {
        is Activity.ListUpdate -> copy(isLiked = like.isLiked, likeCount = like.likeCount)
        is Activity.Text -> copy(isLiked = like.isLiked, likeCount = like.likeCount)
    }
}
