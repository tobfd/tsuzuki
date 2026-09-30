package com.tobfd.tsuzuki.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.R as DesignR
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.SectionHeader
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiSearchField
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.designsystem.theme.bleedHorizontally
import com.tobfd.tsuzuki.core.model.BrowseHome
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.QuickFilter
import com.tobfd.tsuzuki.core.model.SearchResult
import com.tobfd.tsuzuki.core.ui.MediaCoverCard
import com.tobfd.tsuzuki.core.ui.MediaResultRow
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.ScrollToTopOnTabReselect
import com.tobfd.tsuzuki.core.ui.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

/** Root of the Browse tab. */
@Serializable
data object BrowseRoute : NavKey

/** Browse tab content (docs/DESIGN.md, Browse). The app shell draws the top bar. */
@Composable
fun BrowseScreen(
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val viewModel = hiltViewModel<BrowseViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    BrowseContent(
        state = state,
        results = results,
        onQueryChange = viewModel::onQueryChange,
        onTypeChange = viewModel::onTypeChange,
        onQuickFilter = viewModel::onQuickFilter,
        onOpenFilters = viewModel::onOpenFilters,
        onSeeAllNewlyAdded = viewModel::onSeeAllNewlyAdded,
        onRetryRows = viewModel::onRetryRows,
        onOpenMedia = onOpenMedia,
        modifier = modifier,
        contentPadding = contentPadding
    )
    state.draft?.let { draft ->
        BrowseFilterSheet(
            draft = draft,
            type = state.type,
            today = state.today,
            options = state.options,
            onDraftChange = viewModel::onDraftChange,
            onReset = viewModel::onResetDraft,
            onApply = viewModel::onApplyFilters,
            onDismiss = viewModel::onDismissFilters,
            onRetryOptions = viewModel::onRetryOptions
        )
    }
}

@Composable
internal fun BrowseContent(
    state: BrowseUiState,
    results: LazyPagingItems<SearchResult>,
    onQueryChange: (String) -> Unit,
    onTypeChange: (MediaType) -> Unit,
    onQuickFilter: (QuickFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onSeeAllNewlyAdded: () -> Unit,
    onRetryRows: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val listState = rememberLazyGridState()
    ScrollToTopOnTabReselect(listState)
    // A new search or filter starts at its first result.
    LaunchedEffect(state.search) { listState.scrollToItem(0) }

    // The search field, toggle and chips stay put; only the rows or results below them scroll.
    // Horizontal rows scroll edge to edge, so the side padding goes to each item instead.
    CompositionLocalProvider(LocalSidePadding provides PaddingValues(start = start, end = end)) {
        BrowseColumn(
            state = state,
            results = results,
            listState = listState,
            bottomPadding = contentPadding.calculateBottomPadding(),
            onQueryChange = onQueryChange,
            onTypeChange = onTypeChange,
            onQuickFilter = onQuickFilter,
            onOpenFilters = onOpenFilters,
            onSeeAllNewlyAdded = onSeeAllNewlyAdded,
            onRetryRows = onRetryRows,
            onOpenMedia = onOpenMedia,
            modifier = modifier.padding(top = contentPadding.calculateTopPadding())
        )
    }
}

/** Side padding of the tab content (screen margin and window insets), for items that don't scroll sideways. */
private val LocalSidePadding = staticCompositionLocalOf { PaddingValues(horizontal = TsuzukiSpacing.screenMargin) }

@Composable
private fun BrowseColumn(
    state: BrowseUiState,
    results: LazyPagingItems<SearchResult>,
    listState: LazyGridState,
    bottomPadding: Dp,
    onQueryChange: (String) -> Unit,
    onTypeChange: (MediaType) -> Unit,
    onQuickFilter: (QuickFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onSeeAllNewlyAdded: () -> Unit,
    onRetryRows: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Controls(
            state = state,
            onQueryChange = onQueryChange,
            onTypeChange = onTypeChange,
            onQuickFilter = onQuickFilter,
            onOpenFilters = onOpenFilters
        )
        val side = LocalSidePadding.current
        val layoutDirection = LocalLayoutDirection.current
        // Results fill one column on phones and two or three next to each other on tablets.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TsuzukiSizes.rowGridMinWidth),
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = side.calculateStartPadding(layoutDirection),
                end = side.calculateEndPadding(layoutDirection),
                top = TsuzukiSpacing.small,
                bottom = bottomPadding + TsuzukiSpacing.large
            ),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            if (state.search != null) {
                results(results, state.search, onOpenMedia)
            } else {
                idleRows(state.rows, onQuickFilter, onSeeAllNewlyAdded, onRetryRows, onOpenMedia)
            }
        }
    }
}

@Composable
private fun Controls(
    state: BrowseUiState,
    onQueryChange: (String) -> Unit,
    onTypeChange: (MediaType) -> Unit,
    onQuickFilter: (QuickFilter) -> Unit,
    onOpenFilters: () -> Unit
) {
    Column(
        modifier = Modifier.padding(top = TsuzukiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
    ) {
        Row(
            modifier = Modifier.padding(LocalSidePadding.current),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            TsuzukiSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.browse_search_placeholder),
                onClose = if (state.query.isEmpty()) null else ({ onQueryChange("") }),
                closeLabel = stringResource(DesignR.string.designsystem_search_clear),
                modifier = Modifier.weight(1f)
            )
            FilterButton(activeCount = state.filter.activeCount, onClick = onOpenFilters)
        }
        SegmentedToggle(
            options = persistentListOf(stringResource(R.string.browse_anime), stringResource(R.string.browse_manga)),
            selectedIndex = state.type.ordinal,
            onSelect = { onTypeChange(MediaType.entries[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalSidePadding.current)
        )
        LazyRow(
            contentPadding = LocalSidePadding.current,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            items(QuickFilter.entries, key = { it.name }) { chip ->
                FilterChip(
                    selected = state.quickFilter == chip,
                    onClick = { onQuickFilter(chip) },
                    label = { Text(stringResource(chip.labelRes())) }
                )
            }
        }
    }
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    val description = if (activeCount > 0) {
        pluralStringResource(R.plurals.browse_filters_active, activeCount, activeCount)
    } else {
        stringResource(R.string.browse_filters)
    }
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        BadgedBox(badge = { if (activeCount > 0) Badge { Text(activeCount.toString()) } }) {
            Icon(painterResource(TsuzukiIcons.Tune), contentDescription = null)
        }
    }
}

private fun LazyGridScope.idleRows(
    rows: BrowseRows,
    onQuickFilter: (QuickFilter) -> Unit,
    onSeeAllNewlyAdded: () -> Unit,
    onRetry: () -> Unit,
    onOpenMedia: (Int) -> Unit
) {
    when (rows) {
        BrowseRows.Loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) { CenteredProgress() }

        is BrowseRows.Error -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
            ErrorState(
                title = stringResource(R.string.browse_error_title),
                message = rows.error.message(),
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is BrowseRows.Content -> {
            item(key = "trending", span = { GridItemSpan(maxLineSpan) }) {
                MediaRow(
                    title = stringResource(R.string.browse_trending_now),
                    media = rows.home.trending,
                    onSeeAll = { onQuickFilter(QuickFilter.Trending) },
                    onOpenMedia = onOpenMedia
                )
            }
            item(key = "newlyAdded", span = { GridItemSpan(maxLineSpan) }) {
                MediaRow(
                    title = stringResource(R.string.browse_newly_added),
                    media = rows.home.newlyAdded,
                    onSeeAll = onSeeAllNewlyAdded,
                    onOpenMedia = onOpenMedia
                )
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, media: List<MediaLite>, onSeeAll: () -> Unit, onOpenMedia: (Int) -> Unit) {
    if (media.isEmpty()) return
    val side = LocalSidePadding.current
    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .bleedHorizontally(side.calculateStartPadding(layoutDirection), side.calculateEndPadding(layoutDirection))
            .padding(top = TsuzukiSpacing.small, bottom = TsuzukiSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
    ) {
        SectionHeader(
            title = title,
            onSeeAllClick = onSeeAll,
            modifier = Modifier.padding(LocalSidePadding.current)
        )
        LazyRow(
            contentPadding = LocalSidePadding.current,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)
        ) {
            items(media, key = { it.id }) { item ->
                MediaCoverCard(media = item, onClick = { onOpenMedia(item.id) })
            }
        }
    }
}

private fun LazyGridScope.results(
    results: LazyPagingItems<SearchResult>,
    query: BrowseQuery,
    onOpenMedia: (Int) -> Unit
) {
    val refresh = results.loadState.refresh
    when {
        refresh is LoadState.Loading && results.itemCount == 0 -> item(key = "loading", span = {
            GridItemSpan(maxLineSpan)
        }) { CenteredProgress() }

        refresh is LoadState.Error && results.itemCount == 0 -> item(key = "error", span = {
            GridItemSpan(maxLineSpan)
        }) {
            ErrorState(
                title = stringResource(R.string.browse_error_title),
                message = (refresh.error as? AppError ?: AppError.Unknown(refresh.error.message)).message(),
                onRetry = results::retry,
                modifier = Modifier.fillMaxWidth()
            )
        }

        refresh is LoadState.NotLoading && results.itemCount == 0 -> item(key = "empty", span = {
            GridItemSpan(maxLineSpan)
        }) {
            EmptyState(
                icon = painterResource(TsuzukiIcons.Search),
                title = stringResource(R.string.browse_no_results_title),
                message = stringResource(R.string.browse_no_results_message),
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> {
            item(key = "resultsHeader", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.browse_results),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(top = TsuzukiSpacing.small)
                        .semantics { heading() }
                )
            }
            items(
                count = results.itemCount,
                key = results.itemKey { "${query.type}-${it.media.id}" },
                contentType = { "result" }
            ) { index ->
                results[index]?.let { result ->
                    MediaResultRow(
                        media = result.media,
                        listStatus = result.listStatus,
                        onClick = { onOpenMedia(result.media.id) }
                    )
                }
            }
            when (val append = results.loadState.append) {
                is LoadState.Loading -> item(key = "appendLoading", span = {
                    GridItemSpan(maxLineSpan)
                }) { CenteredProgress() }

                is LoadState.Error -> item(key = "appendError", span = { GridItemSpan(maxLineSpan) }) {
                    AppendError(error = append.error, onRetry = results::retry)
                }

                is LoadState.NotLoading -> Unit
            }
        }
    }
}

@Composable
private fun AppendError(error: Throwable, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TsuzukiSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.browse_more_failed),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = (error as? AppError ?: AppError.Unknown(error.message)).message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRetry) { Text(stringResource(DesignR.string.designsystem_retry)) }
    }
}

@Composable
private fun CenteredProgress() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TsuzukiSpacing.extraLarge),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

internal fun QuickFilter.labelRes(): Int = when (this) {
    QuickFilter.Trending -> R.string.browse_quick_trending
    QuickFilter.Top100 -> R.string.browse_quick_top_100
    QuickFilter.ThisSeason -> R.string.browse_quick_this_season
    QuickFilter.TopMovies -> R.string.browse_quick_top_movies
    QuickFilter.TopManhwa -> R.string.browse_quick_top_manhwa
}

private val previewMedia = PreviewListEntries.all.map { it.media }

@ThemePreviews
@Composable
private fun BrowseIdlePreview() {
    TsuzukiTheme {
        BrowseContent(
            state = BrowseUiState(rows = BrowseRows.Content(BrowseHome(previewMedia, previewMedia.reversed()))),
            results = flowOf(PagingData.empty<SearchResult>()).collectAsLazyPagingItems(),
            onQueryChange = {},
            onTypeChange = {},
            onQuickFilter = {},
            onOpenFilters = {},
            onSeeAllNewlyAdded = {},
            onRetryRows = {},
            onOpenMedia = {}
        )
    }
}

@ThemePreviews
@Composable
private fun BrowseResultsPreview() {
    TsuzukiTheme {
        val query =
            BrowseQuery(MediaType.ANIME, search = "fri", filter = QuickFilter.Top100.filter(java.time.LocalDate.MIN))
        BrowseContent(
            state = BrowseUiState(
                query = "fri",
                filter = query.filter,
                quickFilter = QuickFilter.Top100,
                search = query
            ),
            results = flowOf(
                PagingData.from(previewMedia.map { SearchResult(it, listStatus = null) })
            ).collectAsLazyPagingItems(),
            onQueryChange = {},
            onTypeChange = {},
            onQuickFilter = {},
            onOpenFilters = {},
            onSeeAllNewlyAdded = {},
            onRetryRows = {},
            onOpenMedia = {}
        )
    }
}
