package com.tobfd.tsuzuki.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.RejectReason
import com.tobfd.tsuzuki.core.data.list.RejectedChange
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.PagerTabRow
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.TabPager
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiPullToRefresh
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiSearchField
import com.tobfd.tsuzuki.core.designsystem.component.rememberTabPagerState
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.ui.MediaListRow
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.R as UiR
import com.tobfd.tsuzuki.core.ui.ScrollToTopOnTabReselect
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Root of the Lists tab. */
@Serializable
data object ListsRoute : NavKey

/**
 * The Lists tab. [frame] is the app's tab frame (top bar and banner); the screen hands it its
 * top bar actions (search and sort) and its content. Guests see a log-in state instead.
 */
@Composable
fun ListsRoute(
    isGuest: Boolean,
    onLogIn: () -> Unit,
    onOpenMedia: (mediaId: Int) -> Unit,
    onEditEntry: (mediaId: Int) -> Unit,
    onBrowse: () -> Unit,
    frame: @Composable (actions: @Composable RowScope.() -> Unit, content: @Composable (PaddingValues) -> Unit) -> Unit
) {
    if (isGuest) {
        frame({}) { padding ->
            EmptyState(
                icon = painterResource(TsuzukiIcons.List),
                title = stringResource(R.string.lists_guest_title),
                message = stringResource(R.string.lists_guest_message),
                actionLabel = stringResource(R.string.lists_log_in),
                onAction = onLogIn,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
        return
    }
    ListsContent(
        viewModel = hiltViewModel(),
        onOpenMedia = onOpenMedia,
        onEditEntry = onEditEntry,
        onBrowse = onBrowse,
        frame = frame
    )
}

/** The logged-in Lists tab around [viewModel]: snackbars for Undo and errors, then [ListsScreen]. */
@Composable
internal fun ListsContent(
    viewModel: ListsViewModel,
    onOpenMedia: (mediaId: Int) -> Unit,
    onEditEntry: (mediaId: Int) -> Unit,
    onBrowse: () -> Unit,
    frame: @Composable (actions: @Composable RowScope.() -> Unit, content: @Composable (PaddingValues) -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // Refresh errors need the error's text, which is resolved in composition.
    var refreshError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ListsEvent.Completed -> launch {
                    val before = event.before
                    val message = resources.getString(
                        if (before.type ==
                            MediaType.ANIME
                        ) {
                            R.string.lists_completed_anime
                        } else {
                            R.string.lists_completed_manga
                        },
                        before.media.title.userPreferred
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = resources.getString(R.string.lists_undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo(before)
                }

                is ListsEvent.RefreshFailed -> refreshError = event.error
            }
        }
    }
    refreshError?.let { error ->
        val message = error.message()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            refreshError = null
        }
    }
    state.rejected?.let { rejected ->
        val message = rejected.message()
        LaunchedEffect(rejected) {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            viewModel.onRejectionShown(rejected.entryId)
        }
    }

    frame(
        {
            ListsTopBarActions(
                sort = state.sort,
                onSearch = viewModel::onSearchOpened,
                onSortSelected = viewModel::onSortSelected
            )
        }
    ) { padding ->
        ListsScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            contentPadding = padding,
            onTypeSelected = viewModel::onTypeSelected,
            onTabSelected = viewModel::onTabSelected,
            onQueryChange = viewModel::onQueryChange,
            onSearchClosed = viewModel::onSearchClosed,
            onRefresh = viewModel::onRefresh,
            onEditEntry = { onEditEntry(it.mediaId) },
            onOpenMedia = { onOpenMedia(it.mediaId) },
            onPlusOne = { viewModel.onPlusOne(it.id) },
            onStart = { viewModel.onStart(it.id) },
            onBrowse = onBrowse
        )
    }
}

@Composable
private fun RejectedChange.message(): String = when (reason) {
    RejectReason.Validation -> stringResource(
        R.string.lists_rejected_validation,
        title,
        detail ?: stringResource(UiR.string.ui_error_validation)
    )

    RejectReason.NotFound -> stringResource(R.string.lists_rejected_not_found, title)

    RejectReason.Other -> stringResource(R.string.lists_rejected_generic, title)
}

@Composable
internal fun ListsTopBarActions(sort: ListSort, onSearch: () -> Unit, onSortSelected: (ListSort) -> Unit) {
    IconButton(onClick = onSearch) {
        Icon(painterResource(TsuzukiIcons.Search), contentDescription = stringResource(R.string.lists_search))
    }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(painterResource(TsuzukiIcons.Sort), contentDescription = stringResource(R.string.lists_sort))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ListSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        menuOpen = false
                        onSortSelected(option)
                    },
                    trailingIcon = {
                        if (option == sort) Icon(painterResource(TsuzukiIcons.Check), contentDescription = null)
                    }
                )
            }
        }
    }
}

private fun ListSort.labelRes(): Int = when (this) {
    ListSort.Title -> R.string.lists_sort_title
    ListSort.Score -> R.string.lists_sort_score
    ListSort.Progress -> R.string.lists_sort_progress
    ListSort.LastUpdated -> R.string.lists_sort_last_updated
    ListSort.StartDate -> R.string.lists_sort_start_date
}

@Composable
fun ListsScreen(
    state: ListsUiState,
    snackbarHostState: SnackbarHostState,
    onTypeSelected: (MediaType) -> Unit,
    onTabSelected: (ListTabKey) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClosed: () -> Unit,
    onRefresh: () -> Unit,
    onEditEntry: (MediaListEntry) -> Unit,
    onOpenMedia: (MediaListEntry) -> Unit,
    onPlusOne: (MediaListEntry) -> Unit,
    onStart: (MediaListEntry) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val horizontal = Modifier.padding(start = start, end = end)
    val searching = state.searchActive && state.query.isNotBlank()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
            SegmentedToggle(
                options = persistentListOf(
                    stringResource(R.string.lists_type_anime),
                    stringResource(R.string.lists_type_manga)
                ),
                selectedIndex = MediaType.entries.indexOf(state.type),
                onSelect = { onTypeSelected(MediaType.entries[it]) },
                modifier = horizontal.fillMaxWidth()
            )
            if (state.searchActive) {
                TsuzukiSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.lists_search_placeholder),
                    onClose = onSearchClosed,
                    focusOnStart = true,
                    modifier = horizontal.padding(top = TsuzukiSpacing.medium)
                )
            }
            val paged = !searching && state.loaded && state.loadError == null && state.tabs.isNotEmpty()
            val pagerState = rememberListTabPagerState(state.tabs, state.selectedTab, onTabSelected)
            if (paged) {
                ListTabRow(tabs = state.tabs, type = state.type, pagerState = pagerState, edgePadding = start)
            }
            if (state.waitingChanges > 0) {
                WaitingChangesHint(
                    count = state.waitingChanges,
                    modifier = horizontal.padding(top = TsuzukiSpacing.small)
                )
            }
            val listPadding = PaddingValues(
                start = start,
                end = end,
                top = TsuzukiSpacing.medium,
                bottom = contentPadding.calculateBottomPadding()
            )
            TsuzukiPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                if (paged) {
                    ListTabPages(tabs = state.tabs, type = state.type, pagerState = pagerState) { page ->
                        ListRows(
                            rows = state.tabRows.getOrElse(page) { persistentListOf() },
                            scoreFormat = state.scoreFormat,
                            contentPadding = listPadding,
                            isVisiblePage = page == pagerState.currentPage,
                            onEditEntry = onEditEntry,
                            onOpenMedia = onOpenMedia,
                            onPlusOne = onPlusOne,
                            onStart = onStart,
                            onBrowse = onBrowse
                        )
                    }
                } else {
                    ListBody(
                        state = state,
                        searching = searching,
                        contentPadding = listPadding,
                        onRefresh = onRefresh,
                        onEditEntry = onEditEntry,
                        onOpenMedia = onOpenMedia,
                        onPlusOne = onPlusOne,
                        onStart = onStart,
                        onBrowse = onBrowse
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding())
        )
    }
}

/** The pager behind the status and custom list tabs, in step with [selectedTab] (kept by the ViewModel). */
@Composable
internal fun rememberListTabPagerState(
    tabs: List<ListTab>,
    selectedTab: ListTabKey,
    onTabSelected: (ListTabKey) -> Unit
): PagerState = rememberTabPagerState(
    selectedIndex = tabs.indexOfFirst { it.key == selectedTab }.coerceAtLeast(0),
    pageCount = tabs.size,
    onPageSelected = { page -> tabs.getOrNull(page)?.let { onTabSelected(it.key) } }
)

/** Status tabs, then the custom lists; tapping one or swiping the pages below moves the indicator. */
@Composable
internal fun ListTabRow(tabs: List<ListTab>, type: MediaType, pagerState: PagerState, edgePadding: Dp) {
    PagerTabRow(
        pagerState = pagerState,
        scrollable = true,
        edgePadding = edgePadding,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(top = TsuzukiSpacing.small)
    ) { index, selected, onClick ->
        val tab = tabs[index]
        val label = when (val key = tab.key) {
            is ListTabKey.Status -> stringResource(key.status.labelRes(type))
            is ListTabKey.Custom -> key.name
        }
        Tab(
            selected = selected,
            onClick = onClick,
            text = { Text(if (tab.count > 0) stringResource(R.string.lists_tab_count, label, tab.count) else label) }
        )
    }
}

/**
 * One swipeable page per tab (M12). The rows are all local, so the pages next to the visible one are
 * kept ready; each page keeps its scroll position, also per list type.
 */
@Composable
internal fun ListTabPages(
    tabs: List<ListTab>,
    type: MediaType,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    page: @Composable (index: Int) -> Unit
) {
    TabPager(
        pagerState = pagerState,
        pageKey = { index -> "${type.name}/${tabs.getOrNull(index)?.key?.encode() ?: index}" },
        beyondViewportPageCount = 1,
        modifier = modifier.fillMaxSize(),
        content = page
    )
}

@Composable
private fun WaitingChangesHint(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
    ) {
        Icon(
            painter = painterResource(TsuzukiIcons.CloudUpload),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = pluralStringResource(R.plurals.lists_waiting_changes, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListBody(
    state: ListsUiState,
    searching: Boolean,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onEditEntry: (MediaListEntry) -> Unit,
    onOpenMedia: (MediaListEntry) -> Unit,
    onPlusOne: (MediaListEntry) -> Unit,
    onStart: (MediaListEntry) -> Unit,
    onBrowse: () -> Unit
) {
    val loadError = state.loadError
    when {
        !state.loaded -> Spacer(Modifier.fillMaxSize())

        loadError != null -> ScrollableState(contentPadding) {
            ErrorState(
                title = stringResource(R.string.lists_error_title),
                message = loadError.message(),
                onRetry = onRefresh
            )
        }

        state.rows.isEmpty() && searching -> ScrollableState(contentPadding) {
            EmptyState(
                icon = painterResource(TsuzukiIcons.Search),
                title = stringResource(R.string.lists_search_empty_title),
                message = stringResource(R.string.lists_search_empty_message, state.query.trim())
            )
        }

        else -> ListRows(
            rows = state.rows,
            scoreFormat = state.scoreFormat,
            contentPadding = contentPadding,
            onEditEntry = onEditEntry,
            onOpenMedia = onOpenMedia,
            onPlusOne = onPlusOne,
            onStart = onStart,
            onBrowse = onBrowse
        )
    }
}

/** The rows of one tab (or of a search), or its empty state. */
@Composable
private fun ListRows(
    rows: List<MediaListEntry>,
    scoreFormat: ScoreFormat,
    contentPadding: PaddingValues,
    onEditEntry: (MediaListEntry) -> Unit,
    onOpenMedia: (MediaListEntry) -> Unit,
    onPlusOne: (MediaListEntry) -> Unit,
    onStart: (MediaListEntry) -> Unit,
    onBrowse: () -> Unit,
    isVisiblePage: Boolean = true
) {
    if (rows.isEmpty()) {
        ScrollableState(contentPadding) {
            EmptyState(
                icon = painterResource(TsuzukiIcons.List),
                title = stringResource(R.string.lists_empty_title),
                message = stringResource(R.string.lists_empty_message),
                actionLabel = stringResource(R.string.lists_empty_browse),
                onAction = onBrowse
            )
        }
        return
    }
    val gridState = rememberLazyGridState()
    // Pages beside the visible one stay composed; only the visible one scrolls up on reselect.
    if (isVisiblePage) ScrollToTopOnTabReselect(gridState)
    // One column on phones; two or three on a tablet when no detail pane is open.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TsuzukiSizes.rowGridMinWidth),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
        modifier = Modifier.fillMaxSize()
    ) {
        items(rows, key = { it.id }) { entry ->
            MediaListRow(
                entry = entry,
                scoreFormat = scoreFormat,
                onClick = { onEditEntry(entry) },
                onLongClick = { onOpenMedia(entry) },
                onPlusOne = { onPlusOne(entry) },
                onStart = { onStart(entry) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

/** Empty and error states scroll, so pull to refresh works on them too. */
@Composable
internal fun ScrollableState(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun previewState(type: MediaType = MediaType.ANIME) = ListsUiState(
    type = type,
    tabs = listOf(
        ListTab(ListTabKey.Status(MediaListStatus.CURRENT), 3),
        ListTab(ListTabKey.Status(MediaListStatus.PLANNING), 1),
        ListTab(ListTabKey.Status(MediaListStatus.COMPLETED), 0),
        ListTab(ListTabKey.Status(MediaListStatus.PAUSED), 0),
        ListTab(ListTabKey.Status(MediaListStatus.DROPPED), 0),
        ListTab(ListTabKey.Status(MediaListStatus.REPEATING), 0),
        ListTab(ListTabKey.Custom("Favs"), 2)
    ).toImmutableList(),
    rows = PreviewListEntries.all.toImmutableList(),
    tabRows = List(7) { index ->
        if (index == 0) PreviewListEntries.all.toImmutableList() else persistentListOf()
    }.toImmutableList(),
    scoreFormat = ScoreFormat.POINT_10_DECIMAL,
    loaded = true
)

@ThemePreviews
@Composable
private fun ListsScreenPreview() {
    TsuzukiPreview {
        ListsScreen(
            state = previewState(),
            snackbarHostState = remember { SnackbarHostState() },
            onTypeSelected = {},
            onTabSelected = {},
            onQueryChange = {},
            onSearchClosed = {},
            onRefresh = {},
            onEditEntry = {},
            onOpenMedia = {},
            onPlusOne = {},
            onStart = {},
            onBrowse = {},
            contentPadding = PaddingValues(TsuzukiSpacing.large)
        )
    }
}

@ThemePreviews
@Composable
private fun ListsScreenEmptyPreview() {
    TsuzukiPreview {
        ListsScreen(
            state = previewState().copy(
                rows = persistentListOf(),
                tabRows = List(7) { persistentListOf<MediaListEntry>() }.toImmutableList(),
                waitingChanges = 2
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTypeSelected = {},
            onTabSelected = {},
            onQueryChange = {},
            onSearchClosed = {},
            onRefresh = {},
            onEditEntry = {},
            onOpenMedia = {},
            onPlusOne = {},
            onStart = {},
            onBrowse = {},
            contentPadding = PaddingValues(TsuzukiSpacing.large)
        )
    }
}
