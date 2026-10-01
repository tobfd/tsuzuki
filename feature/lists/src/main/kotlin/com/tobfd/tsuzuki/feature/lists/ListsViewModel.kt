package com.tobfd.tsuzuki.feature.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.list.RejectedChange
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListsUiState(
    val type: MediaType = MediaType.ANIME,
    val tabs: ImmutableList<ListTab> = persistentListOf(),
    val selectedTab: ListTabKey = ListTabKey.Status(MediaListStatus.CURRENT),
    val rows: ImmutableList<MediaListEntry> = persistentListOf(),
    /** The rows of every tab, in [tabs] order, for the swipeable pages (search uses [rows]). */
    val tabRows: ImmutableList<ImmutableList<MediaListEntry>> = persistentListOf(),
    val sort: ListSort = ListSort.LastUpdated,
    val searchActive: Boolean = false,
    val query: String = "",
    val scoreFormat: ScoreFormat = ScoreFormat.POINT_10_DECIMAL,
    /** False until Room has delivered the list once. */
    val loaded: Boolean = false,
    /** Nothing of this type on the list at all (not just in the selected tab). */
    val listEmpty: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The first sync failed and there is nothing to show yet. */
    val loadError: AppError? = null,
    /** Changes still waiting to be sent, shown once they wait longer than a moment. */
    val waitingChanges: Int = 0,
    /** A change AniList rejected, to show once. */
    val rejected: RejectedChange? = null
)

sealed interface ListsEvent {
    /** A +1 finished the entry; the snackbar offers Undo, which restores [before]. */
    data class Completed(val before: MediaListEntry) : ListsEvent

    data class RefreshFailed(val error: AppError) : ListsEvent
}

private const val KEY_TYPE = "type"
private const val KEY_TAB = "tab"
private const val KEY_SORT = "sort"
private const val KEY_QUERY = "query"
private const val KEY_SEARCH = "search"

/** Changes that are sent within this time never show the "waiting" hint. */
private const val WAITING_HINT_DELAY_MS = 3_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val listRepository: ListRepository,
    sessionRepository: SessionRepository
) : ViewModel() {

    private val type = savedState.getStateFlow(KEY_TYPE, MediaType.ANIME.name)
        .map { name -> MediaType.entries.firstOrNull { it.name == name } ?: MediaType.ANIME }
    private val tab = savedState.getStateFlow<String?>(KEY_TAB, null).map(ListTabKey::decode)
    private val sort = savedState.getStateFlow(KEY_SORT, ListSort.LastUpdated.name)
        .map { name -> ListSort.entries.firstOrNull { it.name == name } ?: ListSort.LastUpdated }
    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val searchActive = savedState.getStateFlow(KEY_SEARCH, false)

    private val refreshing = MutableStateFlow(false)
    private val loadError = MutableStateFlow<AppError?>(null)

    private val events = Channel<ListsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<ListsEvent> = events.receiveAsFlow()

    private val scoreFormat = sessionRepository.session.map { session ->
        (session as? SessionState.LoggedIn)?.viewer?.options?.scoreFormat ?: ScoreFormat.POINT_10_DECIMAL
    }

    private val waitingChanges = listRepository.queuedChangeCount.mapLatest { count ->
        if (count > 0) delay(WAITING_HINT_DELAY_MS)
        count
    }

    private val filters = combine(tab, sort, query, searchActive) { tab, sort, query, searchActive ->
        Filters(tab, sort, query, searchActive)
    }

    private data class Filters(val tab: ListTabKey?, val sort: ListSort, val query: String, val searchActive: Boolean)

    private val content = type.flatMapLatest { type ->
        combine(listRepository.observeList(type), filters, scoreFormat) { list, filters, scoreFormat ->
            val tabs = tabsOf(list)
            // A custom list that no longer exists falls back to the first tab.
            val selected = filters.tab?.takeIf { key -> tabs.any { it.key == key } } ?: tabs.first().key
            val query = if (filters.searchActive) filters.query else ""
            ListsUiState(
                type = type,
                tabs = tabs.toImmutableList(),
                selectedTab = selected,
                rows = rowsOf(list, selected, query, filters.sort, Locale.getDefault()).toImmutableList(),
                // Local data from Room: every tab is ready, so swiping never waits.
                tabRows = tabs.map { rowsOf(list, it.key, "", filters.sort, Locale.getDefault()).toImmutableList() }
                    .toImmutableList(),
                sort = filters.sort,
                searchActive = filters.searchActive,
                query = filters.query,
                scoreFormat = scoreFormat,
                loaded = true,
                listEmpty = list.entries.isEmpty()
            )
        }
    }

    val uiState: StateFlow<ListsUiState> = combine(
        content,
        refreshing,
        loadError,
        waitingChanges,
        listRepository.rejectedChanges
    ) { content, refreshing, loadError, waiting, rejected ->
        content.copy(
            isRefreshing = refreshing,
            loadError = loadError.takeIf { content.listEmpty },
            waitingChanges = waiting,
            rejected = rejected.firstOrNull()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    init {
        // Usually already synced by the app start; this shows progress on the very first sync.
        refresh(force = false)
    }

    fun onTypeSelected(type: MediaType) {
        savedState[KEY_TYPE] = type.name
        savedState[KEY_TAB] = null
    }

    fun onTabSelected(tab: ListTabKey) {
        savedState[KEY_TAB] = tab.encode()
    }

    fun onSortSelected(sort: ListSort) {
        savedState[KEY_SORT] = sort.name
    }

    fun onSearchOpened() {
        savedState[KEY_SEARCH] = true
    }

    fun onSearchClosed() {
        savedState[KEY_SEARCH] = false
        savedState[KEY_QUERY] = ""
    }

    fun onQueryChange(query: String) {
        savedState[KEY_QUERY] = query
    }

    fun onRefresh() = refresh(force = true)

    private fun refresh(force: Boolean) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val result = listRepository.refresh(force)
            refreshing.value = false
            val error = result.exceptionOrNull() as? AppError
            loadError.value = error
            if (error != null && force) events.send(ListsEvent.RefreshFailed(error))
        }
    }

    fun onPlusOne(entryId: Int) {
        viewModelScope.launch {
            val outcome = listRepository.plusOne(entryId) ?: return@launch
            if (outcome.completed) events.send(ListsEvent.Completed(outcome.before))
        }
    }

    fun onStart(entryId: Int) {
        viewModelScope.launch { listRepository.start(entryId) }
    }

    fun onUndo(before: MediaListEntry) {
        viewModelScope.launch { listRepository.restore(before) }
    }

    fun onRejectionShown(entryId: Int) {
        viewModelScope.launch { listRepository.dismissRejection(entryId) }
    }
}
