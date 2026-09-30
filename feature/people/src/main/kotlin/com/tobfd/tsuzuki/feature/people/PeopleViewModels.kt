package com.tobfd.tsuzuki.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.people.PeopleRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.CharacterAppearance
import com.tobfd.tsuzuki.core.model.CharacterDetail
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FavouriteKind
import com.tobfd.tsuzuki.core.model.ProductionRole
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.StaffDetail
import com.tobfd.tsuzuki.core.model.VoicedCharacter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A list that grows by "Load more": [nextPage] is null once AniList has no more. */
data class PagedItems<T>(val items: List<T>, val nextPage: Int?, val loading: Boolean = false) {
    companion object {
        /** The first page as it came with the page request; the next one is page 2. */
        fun <T> first(page: ContentPage<T>) = PagedItems(page.items, nextPage = if (page.hasNextPage) 2 else null)
    }
}

/** Loading, failed, or [T] with the favourite heart as the viewer sees it right now. */
sealed interface PersonUiState<out T> {
    data object Loading : PersonUiState<Nothing>

    data class Error(val error: AppError) : PersonUiState<Nothing>

    data class Content<T>(val detail: T, val isFavourite: Boolean) : PersonUiState<T>
}

sealed interface PersonEvent {
    /** Guests tapped the heart. */
    data object LogInToUse : PersonEvent

    data class Failed(val error: AppError) : PersonEvent
}

/**
 * Shared by the character and staff pages: one request per open ([load]), an optimistic heart,
 * and "Load more" for the lists below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PersonViewModel<T : Any>(
    private val kind: FavouriteKind,
    protected val id: Int,
    protected val repository: PeopleRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val loadAttempt = MutableStateFlow(0)
    private val favouriteOverride = MutableStateFlow<Boolean?>(null)
    private val events = Channel<PersonEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PersonEvent> = events.receiveAsFlow()

    protected abstract suspend fun load(): Result<T>

    protected abstract fun T.isFavourite(): Boolean

    /** Called with every loaded page, so the lists start from its first page. */
    protected abstract fun onLoaded(detail: T)

    private val detail: Flow<PersonUiState<T>> = loadAttempt.flatMapLatest {
        flow {
            emit(PersonUiState.Loading)
            emit(
                load().fold(
                    onSuccess = { PersonUiState.Content(it, it.isFavourite()) },
                    onFailure = { PersonUiState.Error(it.toAppError()) }
                )
            )
        }
    }.onEach { if (it is PersonUiState.Content) onLoaded(it.detail) }

    val uiState: StateFlow<PersonUiState<T>> = combine(detail, favouriteOverride) { state, favourite ->
        if (state is PersonUiState.Content && favourite != null) state.copy(isFavourite = favourite) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState.Loading)

    fun onRetry() {
        favouriteOverride.value = null
        loadAttempt.value++
    }

    /** Optimistic: the heart changes at once and goes back if AniList says no. */
    fun onToggleFavourite() {
        val content = uiState.value as? PersonUiState.Content ?: return
        viewModelScope.launch {
            if (sessionRepository.session.first() !is SessionState.LoggedIn) {
                events.send(PersonEvent.LogInToUse)
                return@launch
            }
            val before = content.isFavourite
            favouriteOverride.value = !before
            repository.toggleFavourite(kind, id).onFailure {
                favouriteOverride.value = before
                events.send(PersonEvent.Failed(it.toAppError()))
            }
        }
    }

    /** Loads the next page of [list] with [fetch]; failures show as a snackbar and keep what is there. */
    protected fun <E> loadMore(
        list: MutableStateFlow<PagedItems<E>>,
        key: (E) -> Any,
        fetch: suspend (page: Int) -> Result<ContentPage<E>>
    ) {
        val current = list.value
        val page = current.nextPage ?: return
        if (current.loading) return
        list.value = current.copy(loading = true)
        viewModelScope.launch {
            fetch(page)
                .onSuccess { next ->
                    list.update { paged ->
                        val known = paged.items.mapTo(mutableSetOf(), key)
                        PagedItems(
                            items = paged.items + next.items.filter { key(it) !in known },
                            nextPage = if (next.hasNextPage) page + 1 else null
                        )
                    }
                }
                .onFailure {
                    list.update { paged -> paged.copy(loading = false) }
                    events.send(PersonEvent.Failed(it.toAppError()))
                }
        }
    }
}

private fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unknown(message)

/** The character page (docs/ROADMAP.md, M8). */
@HiltViewModel(assistedFactory = CharacterViewModel.Factory::class)
class CharacterViewModel @AssistedInject constructor(
    @Assisted characterId: Int,
    repository: PeopleRepository,
    sessionRepository: SessionRepository
) : PersonViewModel<CharacterDetail>(FavouriteKind.Character, characterId, repository, sessionRepository) {

    @AssistedFactory
    interface Factory {
        fun create(characterId: Int): CharacterViewModel
    }

    private val appearanceList = MutableStateFlow(PagedItems<CharacterAppearance>(emptyList(), null))
    val appearances: StateFlow<PagedItems<CharacterAppearance>> = appearanceList

    override suspend fun load() = repository.character(id)

    override fun CharacterDetail.isFavourite() = isFavourite

    override fun onLoaded(detail: CharacterDetail) {
        appearanceList.value = PagedItems.first(detail.appearances)
    }

    fun onLoadMoreAppearances() = loadMore(appearanceList, key = { it.media.id }) { page ->
        repository.characterAppearances(id, page)
    }
}

/** The staff page (docs/ROADMAP.md, M8). */
@HiltViewModel(assistedFactory = StaffViewModel.Factory::class)
class StaffViewModel @AssistedInject constructor(
    @Assisted staffId: Int,
    repository: PeopleRepository,
    sessionRepository: SessionRepository
) : PersonViewModel<StaffDetail>(FavouriteKind.Staff, staffId, repository, sessionRepository) {

    @AssistedFactory
    interface Factory {
        fun create(staffId: Int): StaffViewModel
    }

    private val characterList = MutableStateFlow(PagedItems<VoicedCharacter>(emptyList(), null))
    val characters: StateFlow<PagedItems<VoicedCharacter>> = characterList

    private val roleList = MutableStateFlow(PagedItems<ProductionRole>(emptyList(), null))
    val roles: StateFlow<PagedItems<ProductionRole>> = roleList

    override suspend fun load() = repository.staff(id)

    override fun StaffDetail.isFavourite() = isFavourite

    override fun onLoaded(detail: StaffDetail) {
        characterList.value = PagedItems.first(detail.characters)
        roleList.value = PagedItems.first(detail.roles)
    }

    fun onLoadMoreCharacters() = loadMore(characterList, key = { it.character.id }) { page ->
        repository.staffCharacters(id, page)
    }

    fun onLoadMoreRoles() = loadMore(roleList, key = { it.media.id to it.role }) { page ->
        repository.staffRoles(id, page)
    }
}
