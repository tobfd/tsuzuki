package com.tobfd.tsuzuki.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.OtherUserList
import com.tobfd.tsuzuki.core.data.list.UserListRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Someone else's list: loading, failed, private, or the rows of the selected tab. */
sealed interface UserListUiState {
    data object Loading : UserListUiState

    data class Error(val error: AppError) : UserListUiState

    data object Private : UserListUiState

    data class Content(
        val type: MediaType,
        val tabs: ImmutableList<ListTab>,
        val selectedTab: ListTabKey,
        val rows: ImmutableList<MediaListEntry>,
        val sort: ListSort,
        val searchActive: Boolean,
        val query: String,
        /** Scores show in the viewer's format, like everywhere else in the app. */
        val scoreFormat: ScoreFormat,
        val listEmpty: Boolean
    ) : UserListUiState
}

/**
 * Another user's anime or manga list, read only (docs/ROADMAP.md, M9): the tabs, sort and search of
 * the Lists tab over data that lives in the Apollo cache only. It loads when the screen first shows.
 */
@HiltViewModel(assistedFactory = UserListViewModel.Factory::class)
class UserListViewModel @AssistedInject constructor(
    @Assisted private val userId: Int,
    @Assisted private val type: MediaType,
    private val repository: UserListRepository,
    sessionRepository: SessionRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(userId: Int, type: MediaType): UserListViewModel
    }

    private sealed interface Load {
        data object Loading : Load

        data class Failed(val error: AppError) : Load

        data class Done(val list: OtherUserList) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)
    private val tab = MutableStateFlow<ListTabKey?>(null)
    private val sort = MutableStateFlow(ListSort.Title)
    private val query = MutableStateFlow("")
    private val searchActive = MutableStateFlow(false)
    private var started = false

    private val scoreFormat = sessionRepository.session.map { session ->
        (session as? SessionState.LoggedIn)?.viewer?.options?.scoreFormat ?: ScoreFormat.POINT_10_DECIMAL
    }

    val uiState: StateFlow<UserListUiState> = combine(
        load,
        combine(tab, sort, query, searchActive, ::Filters),
        scoreFormat
    ) { load, filters, scoreFormat ->
        when (load) {
            Load.Loading -> UserListUiState.Loading

            is Load.Failed -> UserListUiState.Error(load.error)

            is Load.Done -> when (val list = load.list) {
                OtherUserList.Private -> UserListUiState.Private

                is OtherUserList.Visible -> {
                    val tabs = tabsOf(list.list)
                    // Start on the first tab with something in it, like anilist.co.
                    val selected = filters.tab ?: tabs.firstOrNull { it.count > 0 }?.key
                        ?: ListTabKey.Status(MediaListStatus.CURRENT)
                    val search = if (filters.searchActive) filters.query else ""
                    UserListUiState.Content(
                        type = type,
                        tabs = tabs.toImmutableList(),
                        selectedTab = selected,
                        rows = rowsOf(list.list, selected, search, filters.sort, Locale.getDefault()).toImmutableList(),
                        sort = filters.sort,
                        searchActive = filters.searchActive,
                        query = filters.query,
                        scoreFormat = scoreFormat,
                        listEmpty = list.list.entries.isEmpty()
                    )
                }
            }
        }
    }.onStart {
        if (!started) {
            started = true
            loadList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserListUiState.Loading)

    fun onRetry() {
        load.value = Load.Loading
        loadList()
    }

    fun onTabSelected(key: ListTabKey) {
        tab.value = key
    }

    fun onSortSelected(selected: ListSort) {
        sort.value = selected
    }

    fun onSearchOpened() {
        searchActive.value = true
    }

    fun onSearchClosed() {
        searchActive.value = false
        query.value = ""
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    private fun loadList() {
        viewModelScope.launch {
            load.value = repository.list(userId, type).fold(
                onSuccess = { Load.Done(it) },
                onFailure = { Load.Failed(it as? AppError ?: AppError.Unknown(it.message)) }
            )
        }
    }

    private data class Filters(val tab: ListTabKey?, val sort: ListSort, val query: String, val searchActive: Boolean)
}
