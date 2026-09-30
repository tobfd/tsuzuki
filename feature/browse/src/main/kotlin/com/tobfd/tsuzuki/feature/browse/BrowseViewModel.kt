package com.tobfd.tsuzuki.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.browse.BrowseRepository
import com.tobfd.tsuzuki.core.model.BrowseFilter
import com.tobfd.tsuzuki.core.model.BrowseHome
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.BrowseSort
import com.tobfd.tsuzuki.core.model.FilterOptions
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.QuickFilter
import com.tobfd.tsuzuki.core.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Search waits this long after the last key (docs/ANILIST_API.md, Rate limit). */
internal const val SEARCH_DEBOUNCE_MS = 400L

/** Shorter search terms are not sent. */
internal const val MIN_SEARCH_LENGTH = 2

/** The idle rows: trending and newly added. */
sealed interface BrowseRows {
    data object Loading : BrowseRows

    data class Content(val home: BrowseHome) : BrowseRows

    data class Error(val error: AppError) : BrowseRows
}

/** Genres and tags for the filter sheet; loaded when the sheet first opens. */
sealed interface FilterOptionsState {
    data object Loading : FilterOptionsState

    data class Loaded(val options: FilterOptions) : FilterOptionsState

    data class Error(val error: AppError) : FilterOptionsState
}

data class BrowseUiState(
    val query: String = "",
    val type: MediaType = MediaType.ANIME,
    val filter: BrowseFilter = BrowseFilter(),
    val quickFilter: QuickFilter? = null,
    /** What the result list shows; null shows the idle rows. */
    val search: BrowseQuery? = null,
    val rows: BrowseRows = BrowseRows.Loading,
    /** The filter sheet's unsaved choices; null while the sheet is closed. */
    val draft: BrowseFilter? = null,
    val options: FilterOptionsState = FilterOptionsState.Loading,
    val today: LocalDate = LocalDate.MIN
)

/**
 * The Browse tab (docs/ROADMAP.md, M7). Typing waits [SEARCH_DEBOUNCE_MS] and needs
 * [MIN_SEARCH_LENGTH] characters; a new term cancels the running search. Filters and quick chips
 * apply at once. Without a search, filter or chip the idle rows show (one `BrowseHome` request).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(private val repository: BrowseRepository, private val clock: Clock) :
    ViewModel() {

    private val query = MutableStateFlow("")
    private val type = MutableStateFlow(MediaType.ANIME)
    private val filter = MutableStateFlow(BrowseFilter())
    private val quickFilter = MutableStateFlow<QuickFilter?>(null)
    private val draft = MutableStateFlow<BrowseFilter?>(null)
    private val options = MutableStateFlow<FilterOptionsState>(FilterOptionsState.Loading)
    private val rowsAttempt = MutableStateFlow(0)

    /** The search term once typing paused; blank or too short counts as none. */
    private val searchTerm: Flow<String?> = query
        .map { it.trim().takeIf { term -> term.length >= MIN_SEARCH_LENGTH } }
        .distinctUntilChanged()
        .debounce { if (it == null) 0L else SEARCH_DEBOUNCE_MS }

    private val search: Flow<BrowseQuery?> = combine(searchTerm, type, filter, quickFilter) {
            term,
            type,
            filter,
            quick
        ->
        if (term == null && !filter.isActive && quick == null) {
            null
        } else {
            BrowseQuery(type = type, search = term, filter = filter, limit = quick?.limit)
        }
    }.distinctUntilChanged()

    private val rows: Flow<BrowseRows> = combine(type, rowsAttempt) { type, _ -> type }.flatMapLatest { type ->
        flow {
            emit(BrowseRows.Loading)
            emit(
                repository.home(type).fold(
                    onSuccess = { BrowseRows.Content(it) },
                    onFailure = { BrowseRows.Error(it as? AppError ?: AppError.Unknown(it.message)) }
                )
            )
        }
    }

    /** Result pages of the current search; empty while idle. */
    val results: Flow<PagingData<SearchResult>> = search
        .flatMapLatest { query -> if (query == null) flowOf(PagingData.empty()) else repository.search(query) }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<BrowseUiState> = combine(
        combine(query, type, filter, quickFilter, ::Selection),
        search,
        rows,
        draft,
        options
    ) { selection, search, rows, draft, options ->
        BrowseUiState(
            query = selection.query,
            type = selection.type,
            filter = selection.filter,
            quickFilter = selection.quickFilter,
            search = search,
            rows = rows,
            draft = draft,
            options = options,
            today = LocalDate.now(clock)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState(today = LocalDate.now(clock)))

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onTypeChange(newType: MediaType) {
        if (newType == type.value) return
        type.value = newType
        val quick = quickFilter.value
        if (quick?.type != null && quick.type != newType) {
            quickFilter.value = null
            filter.value = BrowseFilter()
        } else {
            filter.update { it.forType(newType) }
        }
    }

    /** Tapping the active chip clears it again. */
    fun onQuickFilter(chip: QuickFilter) {
        if (quickFilter.value == chip) {
            quickFilter.value = null
            filter.value = BrowseFilter()
            return
        }
        chip.type?.let { type.value = it }
        quickFilter.value = chip
        filter.value = chip.filter(LocalDate.now(clock))
    }

    /** "See all" on the Newly added row. */
    fun onSeeAllNewlyAdded() {
        quickFilter.value = null
        filter.value = BrowseFilter(sort = BrowseSort.RecentlyAdded)
    }

    fun onRetryRows() {
        rowsAttempt.value++
    }

    fun onOpenFilters() {
        draft.value = filter.value
        if (options.value !is FilterOptionsState.Loaded) loadOptions()
    }

    fun onDraftChange(changed: BrowseFilter) {
        draft.value = changed.forType(type.value)
    }

    fun onResetDraft() {
        draft.value = BrowseFilter()
    }

    /** "Show results": the sheet's choices replace the filter; a quick chip no longer matches them. */
    fun onApplyFilters() {
        val chosen = draft.value ?: return
        if (chosen != filter.value) quickFilter.value = null
        filter.value = chosen
        draft.value = null
    }

    fun onDismissFilters() {
        draft.value = null
    }

    fun onRetryOptions() = loadOptions()

    private fun loadOptions() {
        options.value = FilterOptionsState.Loading
        viewModelScope.launch {
            options.value = repository.filterOptions().fold(
                onSuccess = { FilterOptionsState.Loaded(it) },
                onFailure = { FilterOptionsState.Error(it as? AppError ?: AppError.Unknown(it.message)) }
            )
        }
    }

    private data class Selection(
        val query: String,
        val type: MediaType,
        val filter: BrowseFilter,
        val quickFilter: QuickFilter?
    )
}
