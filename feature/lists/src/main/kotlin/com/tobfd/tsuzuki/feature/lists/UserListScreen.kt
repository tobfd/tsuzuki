package com.tobfd.tsuzuki.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiSearchField
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.ui.MediaListRow
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/** Another user's anime or manga list, read only; [userName] titles the page. */
@Serializable
data class UserListRoute(val userId: Int, val userName: String, val type: MediaType) : NavKey

/**
 * Another user's list (docs/ROADMAP.md, M9): the Lists tab's status tabs, sort and search without
 * +1, Start or the editor. A tap opens the detail page.
 */
@Composable
fun UserListScreen(
    userId: Int,
    userName: String,
    type: MediaType,
    onBack: () -> Unit,
    onOpenMedia: (mediaId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<UserListViewModel, UserListViewModel.Factory>(
        key = "userList-$userId-$type",
        creationCallback = { it.create(userId, type) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserListContent(
        state = state,
        userName = userName,
        type = type,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onTabSelected = viewModel::onTabSelected,
        onSortSelected = viewModel::onSortSelected,
        onSearchOpened = viewModel::onSearchOpened,
        onSearchClosed = viewModel::onSearchClosed,
        onQueryChange = viewModel::onQueryChange,
        onOpenMedia = onOpenMedia,
        modifier = modifier
    )
}

@Composable
internal fun UserListContent(
    state: UserListUiState,
    userName: String,
    type: MediaType,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTabSelected: (ListTabKey) -> Unit,
    onSortSelected: (ListSort) -> Unit,
    onSearchOpened: () -> Unit,
    onSearchClosed: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenMedia: (mediaId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(
        if (type == MediaType.ANIME) R.string.lists_user_title_anime else R.string.lists_user_title_manga,
        userName
    )
    Scaffold(
        modifier = modifier,
        topBar = {
            TsuzukiBackTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    if (state is UserListUiState.Content && !state.listEmpty) {
                        ListsTopBarActions(
                            sort = state.sort,
                            onSearch = onSearchOpened,
                            onSortSelected = onSortSelected
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val start = innerPadding.calculateStartPadding(layoutDirection) + TsuzukiSpacing.screenMargin
        val end = innerPadding.calculateEndPadding(layoutDirection) + TsuzukiSpacing.screenMargin
        val bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.large
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            when (state) {
                UserListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is UserListUiState.Error -> ErrorState(
                    title = stringResource(R.string.lists_user_error_title),
                    message = state.error.message(),
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center)
                )

                UserListUiState.Private -> EmptyState(
                    icon = painterResource(TsuzukiIcons.List),
                    title = stringResource(R.string.lists_user_private_title),
                    message = stringResource(R.string.lists_user_private_message, userName),
                    modifier = Modifier.align(Alignment.Center)
                )

                is UserListUiState.Content -> UserListBody(
                    state = state,
                    contentPadding = PaddingValues(start = start, end = end, bottom = bottom),
                    onTabSelected = onTabSelected,
                    onSearchClosed = onSearchClosed,
                    onQueryChange = onQueryChange,
                    onOpenMedia = onOpenMedia
                )
            }
        }
    }
}

@Composable
private fun UserListBody(
    state: UserListUiState.Content,
    contentPadding: PaddingValues,
    onTabSelected: (ListTabKey) -> Unit,
    onSearchClosed: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenMedia: (mediaId: Int) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val searching = state.searchActive && state.query.isNotBlank()
    Column {
        if (state.searchActive) {
            TsuzukiSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.lists_user_search_placeholder),
                onClose = onSearchClosed,
                focusOnStart = true,
                modifier = Modifier.padding(start = start, end = end)
            )
        }
        if (!searching && !state.listEmpty) {
            ListTabs(
                tabs = state.tabs,
                selectedTab = state.selectedTab,
                type = state.type,
                onTabSelected = onTabSelected,
                edgePadding = start
            )
        }
        val listPadding = PaddingValues(
            start = start,
            end = end,
            top = TsuzukiSpacing.medium,
            bottom = contentPadding.calculateBottomPadding()
        )
        when {
            state.rows.isEmpty() -> ScrollableState(listPadding) {
                EmptyState(
                    icon = painterResource(if (searching) TsuzukiIcons.Search else TsuzukiIcons.List),
                    title = stringResource(
                        if (searching) R.string.lists_search_empty_title else R.string.lists_user_empty_title
                    ),
                    message = if (searching) {
                        stringResource(R.string.lists_user_search_empty_message, state.query.trim())
                    } else {
                        stringResource(R.string.lists_user_empty_message)
                    }
                )
            }

            else -> LazyColumn(
                state = rememberLazyListState(),
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.rows, key = { it.id }) { entry ->
                    MediaListRow(
                        entry = entry,
                        scoreFormat = state.scoreFormat,
                        onClick = { onOpenMedia(entry.mediaId) },
                        onLongClick = { onOpenMedia(entry.mediaId) },
                        onPlusOne = {},
                        onStart = {},
                        readOnly = true,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun UserListContentPreview() {
    TsuzukiTheme {
        UserListContent(
            state = UserListUiState.Content(
                type = MediaType.ANIME,
                tabs = persistentListOf(
                    ListTab(ListTabKey.Status(MediaListStatus.CURRENT), 3),
                    ListTab(ListTabKey.Status(MediaListStatus.PLANNING), 1)
                ),
                selectedTab = ListTabKey.Status(MediaListStatus.CURRENT),
                rows = persistentListOf(
                    PreviewListEntries.frieren,
                    PreviewListEntries.apothecary,
                    PreviewListEntries.onePiece
                ),
                sort = ListSort.Title,
                searchActive = false,
                query = "",
                scoreFormat = ScoreFormat.POINT_10_DECIMAL,
                listEmpty = false
            ),
            userName = "GeckoTV",
            type = MediaType.ANIME,
            onBack = {},
            onRetry = {},
            onTabSelected = {},
            onSortSelected = {},
            onSearchOpened = {},
            onSearchClosed = {},
            onQueryChange = {},
            onOpenMedia = {}
        )
    }
}

@ThemePreviews
@Composable
private fun UserListPrivatePreview() {
    TsuzukiTheme {
        UserListContent(
            state = UserListUiState.Private,
            userName = "GeckoTV",
            type = MediaType.MANGA,
            onBack = {},
            onRetry = {},
            onTabSelected = {},
            onSortSelected = {},
            onSearchOpened = {},
            onSearchClosed = {},
            onQueryChange = {},
            onOpenMedia = {}
        )
    }
}
