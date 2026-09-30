package com.tobfd.tsuzuki.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.home.HomeRepository
import com.tobfd.tsuzuki.core.data.profile.ProfileRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FollowUser
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.UserProfile
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One list of the Social tab. */
sealed interface FollowListState {
    data object Loading : FollowListState

    data class Loaded(val page: ContentPage<FollowUser>) : FollowListState

    data class Error(val error: AppError) : FollowListState
}

/** Which list the Social tab shows. */
enum class FollowList {
    Following,
    Followers
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Error(val error: AppError) : ProfileUiState

    data class Content(
        val profile: UserProfile,
        /** The viewer's own profile: no follow button. */
        val isOwn: Boolean,
        val isFollowing: Boolean,
        val isRefreshing: Boolean,
        val followList: FollowList,
        val followLists: Map<FollowList, FollowListState>,
        /** The heatmap's last day (UTC, like AniList's activity history). */
        val today: LocalDate
    ) : ProfileUiState
}

sealed interface ProfileEvent {
    /** Guests tapped follow or a like. */
    data object LogInToUse : ProfileEvent

    data class Failed(val error: AppError) : ProfileEvent
}

/**
 * A profile (docs/ROADMAP.md, M9): one `UserProfile` request when it first shows and on pull to
 * refresh, a Social list only when its tab is opened, optimistic follow and likes.
 */
@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
class ProfileViewModel @AssistedInject constructor(
    @Assisted private val userId: Int,
    private val profileRepository: ProfileRepository,
    private val homeRepository: HomeRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(userId: Int): ProfileViewModel
    }

    private sealed interface Load {
        data object Loading : Load

        data class Failed(val error: AppError) : Load

        data class Done(val profile: UserProfile) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val refreshing = MutableStateFlow(false)
    private val followOverride = MutableStateFlow<Boolean?>(null)
    private val likes = MutableStateFlow<Map<Int, Pair<Boolean, Int>>>(emptyMap())
    private val followList = MutableStateFlow(FollowList.Following)
    private val followLists = MutableStateFlow<Map<FollowList, FollowListState>>(emptyMap())
    private val events = Channel<ProfileEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ProfileEvent> = events.receiveAsFlow()
    private var started = false

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(load, refreshing, followOverride, likes, ::Parts),
        followList,
        followLists,
        sessionRepository.session
    ) { parts, list, lists, session ->
        when (val current = parts.load) {
            Load.Loading -> ProfileUiState.Loading

            is Load.Failed -> ProfileUiState.Error(current.error)

            is Load.Done -> {
                val profile = current.profile
                ProfileUiState.Content(
                    profile = profile.copy(
                        recentActivity = profile.recentActivity.map {
                            it.withLike(parts.likes[it.id])
                        }
                    ),
                    isOwn = (session as? SessionState.LoggedIn)?.viewer?.id == profile.id,
                    isFollowing = parts.follow ?: profile.isFollowing,
                    isRefreshing = parts.refreshing,
                    followList = list,
                    followLists = lists,
                    today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
                )
            }
        }
    }.onStart {
        if (!started) {
            started = true
            loadProfile()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    fun onRetry() {
        load.value = Load.Loading
        loadProfile()
    }

    /** Pull to refresh: the page stays while the new one loads; a failure is a snackbar. */
    fun onRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        followLists.value = emptyMap()
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            profileRepository.profile(userId)
                .onSuccess {
                    load.value = Load.Done(it)
                    followOverride.value = null
                    likes.value = emptyMap()
                }
                .onFailure {
                    val error = it.toAppError()
                    if (load.value is Load.Done) {
                        events.send(ProfileEvent.Failed(error))
                    } else {
                        load.value =
                            Load.Failed(error)
                    }
                }
            refreshing.value = false
            if (followLists.value.isEmpty() && followRequested) loadFollowList(followList.value)
        }
    }

    /** Set once the Social tab was opened, so a refresh loads its list again. */
    private var followRequested = false

    /** The Social tab shows [list]; it loads the first time. */
    fun onShowFollowList(list: FollowList) {
        followRequested = true
        followList.value = list
        val state = followLists.value[list]
        if (state == null || state is FollowListState.Error) loadFollowList(list)
    }

    private fun loadFollowList(list: FollowList) {
        followLists.update { it + (list to FollowListState.Loading) }
        viewModelScope.launch {
            val result = profileRepository.follows(userId, followers = list == FollowList.Followers)
            followLists.update {
                it + (
                    list to result.fold(
                        onSuccess = { page -> FollowListState.Loaded(page) },
                        onFailure = { error -> FollowListState.Error(error.toAppError()) }
                    )
                    )
            }
        }
    }

    /** Optimistic: the button changes at once and goes back if AniList says no. */
    fun onToggleFollow() {
        val content = uiState.value as? ProfileUiState.Content ?: return
        if (content.isOwn) return
        viewModelScope.launch {
            if (!loggedIn()) {
                events.send(ProfileEvent.LogInToUse)
                return@launch
            }
            val before = content.isFollowing
            followOverride.value = !before
            profileRepository.toggleFollow(userId)
                .onSuccess { followOverride.value = it }
                .onFailure {
                    followOverride.value = before
                    events.send(ProfileEvent.Failed(it.toAppError()))
                }
        }
    }

    /** Optimistic like on a recent activity. */
    fun onToggleLike(activity: Activity) {
        viewModelScope.launch {
            if (!loggedIn()) {
                events.send(ProfileEvent.LogInToUse)
                return@launch
            }
            val before = likes.value[activity.id]
            val liked = before?.first ?: activity.isLiked
            val count = before?.second ?: activity.likeCount
            likes.update { it + (activity.id to (!liked to (count + if (liked) -1 else 1).coerceAtLeast(0))) }
            homeRepository.toggleLike(activity.id).onFailure {
                likes.update { current ->
                    if (before ==
                        null
                    ) {
                        current - activity.id
                    } else {
                        current + (activity.id to before)
                    }
                }
                events.send(ProfileEvent.Failed(it.toAppError()))
            }
        }
    }

    private suspend fun loggedIn() = sessionRepository.session.first() is SessionState.LoggedIn

    private data class Parts(
        val load: Load,
        val refreshing: Boolean,
        val follow: Boolean?,
        val likes: Map<Int, Pair<Boolean, Int>>
    )
}

private fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unknown(message)

private fun Activity.withLike(like: Pair<Boolean, Int>?): Activity = when {
    like == null -> this
    this is Activity.ListUpdate -> copy(isLiked = like.first, likeCount = like.second)
    this is Activity.Text -> copy(isLiked = like.first, likeCount = like.second)
    else -> this
}
