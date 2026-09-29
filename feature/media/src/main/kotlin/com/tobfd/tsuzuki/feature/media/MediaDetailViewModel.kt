package com.tobfd.tsuzuki.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.media.MediaRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.MediaDetail
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.Viewer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MediaDetailUiState {
    data object Loading : MediaDetailUiState

    data class Error(val error: AppError) : MediaDetailUiState

    data class Content(
        val detail: MediaDetail,
        /** The viewer's entry from Room, so the list button shows edits at once, even offline. */
        val entry: MediaListEntry?,
        /** Null for guests. */
        val viewer: Viewer?,
        val isFavourite: Boolean,
        /** "Add to list" is on its way to AniList. */
        val adding: Boolean = false
    ) : MediaDetailUiState {
        val scoreFormat: ScoreFormat
            get() = viewer?.options?.scoreFormat ?: ScoreFormat.POINT_10_DECIMAL
    }
}

sealed interface MediaDetailEvent {
    data class OpenEditor(val mediaId: Int) : MediaDetailEvent

    /** Guests tapped the list button or the heart. */
    data object LogInToUse : MediaDetailEvent

    data class Failed(val error: AppError) : MediaDetailEvent
}

/**
 * The detail page (docs/ROADMAP.md, M6): one `MediaDetail` request per open, the list button from
 * Room, favourite and "Add to list" talk to AniList.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MediaDetailViewModel.Factory::class)
class MediaDetailViewModel @AssistedInject constructor(
    @Assisted private val mediaId: Int,
    private val mediaRepository: MediaRepository,
    private val listRepository: ListRepository,
    sessionRepository: SessionRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(mediaId: Int): MediaDetailViewModel
    }

    private val loadAttempt = MutableStateFlow(0)
    private val favouriteOverride = MutableStateFlow<Boolean?>(null)
    private val adding = MutableStateFlow(false)
    private val events = Channel<MediaDetailEvent>(Channel.BUFFERED)
    val eventFlow: Flow<MediaDetailEvent> = events.receiveAsFlow()

    private val viewer = sessionRepository.session.map { (it as? SessionState.LoggedIn)?.viewer }

    private val detail = loadAttempt.flatMapLatest { mediaRepository.observeDetail(mediaId) }

    val uiState: StateFlow<MediaDetailUiState> = combine(
        detail,
        listRepository.observeEntry(mediaId),
        viewer,
        favouriteOverride,
        adding
    ) { result, entry, viewer, favourite, adding ->
        result.fold(
            onSuccess = { detail ->
                MediaDetailUiState.Content(
                    detail = detail,
                    entry = entry,
                    viewer = viewer,
                    isFavourite = favourite ?: detail.isFavourite,
                    adding = adding
                )
            },
            onFailure = { MediaDetailUiState.Error(it as? AppError ?: AppError.Unknown(it.message)) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaDetailUiState.Loading)

    fun onRetry() {
        loadAttempt.value++
    }

    /** Opens the editor; a media not on the list is added as Planning first. */
    fun onListButton() {
        val content = uiState.value as? MediaDetailUiState.Content ?: return
        viewModelScope.launch {
            when {
                content.viewer == null -> events.send(MediaDetailEvent.LogInToUse)

                content.entry != null -> events.send(MediaDetailEvent.OpenEditor(mediaId))

                content.adding -> Unit

                else -> {
                    adding.value = true
                    listRepository.add(mediaId)
                        .onSuccess { events.send(MediaDetailEvent.OpenEditor(mediaId)) }
                        .onFailure {
                            events.send(MediaDetailEvent.Failed(it as? AppError ?: AppError.Unknown(it.message)))
                        }
                    adding.value = false
                }
            }
        }
    }

    /** Optimistic: the heart changes at once and goes back if AniList says no. */
    fun onToggleFavourite() {
        val content = uiState.value as? MediaDetailUiState.Content ?: return
        viewModelScope.launch {
            if (content.viewer == null) {
                events.send(MediaDetailEvent.LogInToUse)
                return@launch
            }
            val before = content.isFavourite
            favouriteOverride.value = !before
            mediaRepository.toggleFavourite(mediaId, content.detail.media.type).onFailure {
                favouriteOverride.value = before
                events.send(MediaDetailEvent.Failed(it as? AppError ?: AppError.Unknown(it.message)))
            }
        }
    }
}
