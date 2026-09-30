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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
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
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiPullToRefresh
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiSearchField
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
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
            if (!searching && state.tabs.isNotEmpty()) {
                ListTabs(
                    tabs = state.tabs,
                    selectedTab = state.selectedTab,
                    type = state.type,
                    onTabSelected = onTabSelected,
                    edgePadding = start
                )
            }
            if (state.waitingChanges > 0) {
                WaitingChangesHint(
                    count = state.waitingChanges,
                    modifier = horizontal.padding(top = TsuzukiSpacing.small)
                )
            }
            TsuzukiPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                ListBody(
                    state = state,
                    searching = searching,
                    contentPadding = PaddingValues(
                        start = start,
                        end = end,
                        top = TsuzukiSpacing.medium,
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                    onRefresh = onRefresh,
                    onEditEntry = onEditEntry,
                    onOpenMedia = onOpenMedia,
                    onPlusOne = onPlusOne,
                    onStart = onStart,
                    onBrowse = onBrowse
                )
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

@Composable
internal fun ListTabs(
    tabs: List<ListTab>,
    selectedTab: ListTabKey,
    type: MediaType,
    onTabSelected: (ListTabKey) -> Unit,
    edgePadding: Dp
) {
    val selectedIndex = tabs.indexOfFirst { it.key == selectedTab }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = edgePadding,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(top = TsuzukiSpacing.small)
    ) {
        tabs.forEachIndexed { index, tab ->
            val label = when (val key = tab.key) {
                is ListTabKey.Status -> stringResource(key.status.labelRes(type))
                is ListTabKey.Custom -> key.name
            }
            Tab(
                selected = index == selectedIndex,
                onClick = { onTabSelected(tab.key) },
                text = {
                    Text(if (tab.count > 0) stringResource(R.string.lists_tab_count, label, tab.count) else label)
                }
            )
        }
    }
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

        state.rows.isEmpty() -> ScrollableState(contentPadding) {
            EmptyState(
                icon = painterResource(TsuzukiIcons.List),
                title = stringResource(R.string.lists_empty_title),
                message = stringResource(R.string.lists_empty_message),
                actionLabel = stringResource(R.string.lists_empty_browse),
                onAction = onBrowse
            )
        }

        else -> {
            val listState = rememberLazyListState()
            ScrollToTopOnTabReselect(listState)
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.rows, key = { it.id }) { entry ->
                    MediaListRow(
                        entry = entry,
                        scoreFormat = state.scoreFormat,
                        onClick = { onEditEntry(entry) },
                        onLongClick = { onOpenMedia(entry) },
                        onPlusOne = { onPlusOne(entry) },
                        onStart = { onStart(entry) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
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
            state = previewState().copy(rows = persistentListOf(), waitingChanges = 2),
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
