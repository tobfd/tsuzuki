package com.tobfd.tsuzuki.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tobfd.tsuzuki.core.common.AppError
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

data class HomeUiState(
    val isGuest: Boolean = false,
    val inProgress: ImmutableList<MediaListEntry> = persistentListOf(),
    val upNext: ImmutableList<MediaListEntry> = persistentListOf(),
    val trending: ImmutableList<MediaLite> = persistentListOf(),
    val feedScope: FeedScope = FeedScope.Following,
    val likes: ImmutableMap<Int, LikeState> = persistentMapOf()
)

sealed interface HomeEvent {
    /** A +1 finished the entry; the snackbar offers Undo, which restores [before]. */
    data class Completed(val before: MediaListEntry) : HomeEvent

    data class LikeFailed(val error: AppError) : HomeEvent
}

private const val KEY_SCOPE = "feedScope"

@OptIn(ExperimentalCoroutinesApi::class)
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

    /**
     * The first page comes from the Apollo cache when it is there, so Home shows at once; then the
     * feed is loaded again from the network (one request: feed page and trending together).
     */
    private data class FeedKey(val scope: FeedScope, val fromCache: Boolean)

    private val feedKey = MutableStateFlow<FeedKey?>(null)

    val feed: Flow<PagingData<Activity>> = feedKey
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(PagingData.empty())
            } else {
                homeRepository.feed(key.scope, key.fromCache) { fromCache ->
                    if (fromCache) {
                        feedKey.update { current ->
                            if (current ==
                                key
                            ) {
                                key.copy(fromCache = false)
                            } else {
                                current
                            }
                        }
                    }
                }
            }
        }
        .cachedIn(viewModelScope)

    private val lists =
        combine(listRepository.observeList(MediaType.ANIME), listRepository.observeList(MediaType.MANGA)) {
                anime,
                manga
            ->
            anime.entries + manga.entries
        }

    val uiState: StateFlow<HomeUiState> = combine(isGuest, lists, homeRepository.trending, scope, likes) {
            guest,
            entries,
            trending,
            scope,
            likes
        ->
        HomeUiState(
            isGuest = guest,
            inProgress = inProgressOf(entries).toImmutableList(),
            upNext = upNextOf(entries).toImmutableList(),
            trending = trending.toImmutableList(),
            feedScope = scope,
            likes = likes.toPersistentMap()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            scope.collect { scope -> feedKey.value = FeedKey(scope, fromCache = true) }
        }
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
        feedKey.update { it?.copy(fromCache = false) }
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
