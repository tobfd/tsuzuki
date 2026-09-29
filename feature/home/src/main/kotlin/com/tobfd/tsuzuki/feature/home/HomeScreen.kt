package com.tobfd.tsuzuki.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.home.FeedScope
import com.tobfd.tsuzuki.core.designsystem.component.PlusOneButton
import com.tobfd.tsuzuki.core.designsystem.component.SectionHeader
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiPullToRefresh
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.ListEntryActions
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.ui.ActivityCard
import com.tobfd.tsuzuki.core.ui.MediaCover
import com.tobfd.tsuzuki.core.ui.MediaCoverCard
import com.tobfd.tsuzuki.core.ui.ScrollToTopOnTabReselect
import com.tobfd.tsuzuki.core.ui.coverColorOrNull
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.message
import com.tobfd.tsuzuki.core.ui.progressText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Root of the Home tab. */
@Serializable
data object HomeRoute : NavKey

private val InProgressTitleHeight = 48.dp
private val UpNextCoverWidth = 40.dp

/**
 * The Home tab (docs/DESIGN.md, Home): In Progress and Up next from Room, then Trending now and
 * the activity feed. The app shell draws the top bar; [contentPadding] keeps clear of it.
 */
@Composable
fun HomeScreen(
    onOpenMedia: (mediaId: Int) -> Unit,
    onOpenUser: (name: String) -> Unit,
    onEditEntry: (mediaId: Int) -> Unit,
    onSeeAllLists: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    HomeContent(
        viewModel = hiltViewModel(),
        onOpenMedia = onOpenMedia,
        onOpenUser = onOpenUser,
        onEditEntry = onEditEntry,
        onSeeAllLists = onSeeAllLists,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
internal fun HomeContent(
    viewModel: HomeViewModel,
    onOpenMedia: (mediaId: Int) -> Unit,
    onOpenUser: (name: String) -> Unit,
    onEditEntry: (mediaId: Int) -> Unit,
    onSeeAllLists: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feed = viewModel.feed.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var likeError by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.Completed -> launch {
                    val before = event.before
                    val message = resources.getString(
                        if (before.type ==
                            MediaType.ANIME
                        ) {
                            R.string.home_completed_anime
                        } else {
                            R.string.home_completed_manga
                        },
                        before.media.title.userPreferred
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = resources.getString(R.string.home_undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo(before)
                }

                is HomeEvent.LikeFailed -> likeError = event.error
            }
        }
    }
    likeError?.let { error ->
        val message = error.message()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            likeError = null
        }
    }

    val refreshing = feed.loadState.refresh is LoadState.Loading && feed.itemCount > 0
    Box(modifier = modifier.fillMaxSize()) {
        TsuzukiPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            HomeList(
                state = state,
                feed = feed,
                contentPadding = contentPadding,
                onOpenMedia = onOpenMedia,
                onOpenUser = onOpenUser,
                onEditEntry = onEditEntry,
                onSeeAllLists = onSeeAllLists,
                onPlusOne = viewModel::onPlusOne,
                onStart = viewModel::onStart,
                onFeedScopeSelected = viewModel::onFeedScopeSelected,
                onToggleLike = viewModel::onToggleLike
            )
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
private fun HomeList(
    state: HomeUiState,
    feed: LazyPagingItems<Activity>,
    contentPadding: PaddingValues,
    onOpenMedia: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
    onEditEntry: (Int) -> Unit,
    onSeeAllLists: () -> Unit,
    onPlusOne: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onFeedScopeSelected: (FeedScope) -> Unit,
    onToggleLike: (Activity) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(layoutDirection)
    val end = contentPadding.calculateEndPadding(layoutDirection)
    val horizontal = Modifier.padding(start = start, end = end)
    val listState = rememberLazyListState()
    ScrollToTopOnTabReselect(listState)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap),
        modifier = Modifier.fillMaxSize()
    ) {
        // One item for everything above the feed: sections that fill in later (Room, the trending
        // row) then grow the list at the top instead of pushing the first visible item away.
        item(key = "top") {
            Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)) {
                if (state.inProgress.isNotEmpty()) {
                    SectionHeader(
                        title = stringResource(R.string.home_in_progress),
                        onSeeAllClick = onSeeAllLists,
                        modifier = horizontal
                    )
                    LazyRow(
                        contentPadding = PaddingValues(start = start, end = end),
                        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)
                    ) {
                        items(state.inProgress, key = { it.id }) { entry ->
                            InProgressCard(
                                entry = entry,
                                onClick = { onEditEntry(entry.mediaId) },
                                onPlusOne = { onPlusOne(entry.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
                if (state.upNext.isNotEmpty()) {
                    SectionHeader(
                        title = stringResource(R.string.home_up_next),
                        modifier = horizontal.padding(top = TsuzukiSpacing.large)
                    )
                    state.upNext.forEach { entry ->
                        UpNextRow(
                            entry = entry,
                            onClick = { onOpenMedia(entry.mediaId) },
                            onStart = { onStart(entry.id) },
                            modifier = horizontal
                        )
                    }
                }
                if (state.trending.isNotEmpty()) {
                    SectionHeader(
                        title = stringResource(R.string.home_trending),
                        modifier = horizontal.padding(top = TsuzukiSpacing.large)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(start = start, end = end),
                        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)
                    ) {
                        items(state.trending, key = { it.id }) { media ->
                            MediaCoverCard(media = media, onClick = { onOpenMedia(media.id) })
                        }
                    }
                }
                FeedHeader(
                    state = state,
                    onFeedScopeSelected = onFeedScopeSelected,
                    modifier = horizontal.padding(top = TsuzukiSpacing.large)
                )
            }
        }
        items(count = feed.itemCount, key = feed.itemKey { "activity-${it.id}" }) { index ->
            val activity = feed[index] ?: return@items
            ActivityCard(
                activity = activity.withLike(state.likes[activity.id]),
                onLikeClick = { onToggleLike(activity) },
                onUserClick = { onOpenUser(activity.user.name) },
                onMediaClick = onOpenMedia,
                modifier = horizontal
            )
        }
        item(key = "feedState") {
            FeedLoadState(feed = feed, isGuest = state.isGuest, modifier = horizontal)
        }
    }
}

@Composable
private fun FeedHeader(state: HomeUiState, onFeedScopeSelected: (FeedScope) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        SectionHeader(title = stringResource(R.string.home_activity))
        if (!state.isGuest) {
            SegmentedToggle(
                options = persistentListOf(
                    stringResource(R.string.home_following),
                    stringResource(R.string.home_global)
                ),
                selectedIndex = FeedScope.entries.indexOf(state.feedScope),
                onSelect = { onFeedScopeSelected(FeedScope.entries[it]) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Loading, error with retry, or the empty Following feed below the cards. */
@Composable
private fun FeedLoadState(feed: LazyPagingItems<Activity>, isGuest: Boolean, modifier: Modifier = Modifier) {
    val refresh = feed.loadState.refresh
    val append = feed.loadState.append
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TsuzukiSpacing.large),
        contentAlignment = Alignment.Center
    ) {
        when {
            (refresh is LoadState.Loading && feed.itemCount == 0) || append is LoadState.Loading ->
                CircularProgressIndicator()

            refresh is LoadState.Error && feed.itemCount == 0 -> FeedError((refresh.error as? AppError), feed::retry)

            append is LoadState.Error -> FeedError((append.error as? AppError), feed::retry)

            refresh is LoadState.NotLoading && feed.itemCount == 0 -> Text(
                text = stringResource(
                    if (isGuest) R.string.home_feed_empty_global else R.string.home_feed_empty_following
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeedError(error: AppError?, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = (error ?: AppError.Unknown(null)).message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
    }
}

/** A 144 dp card: cover with "EP x / y" and progress bar, title, progress and +1. */
@Composable
internal fun InProgressCard(
    entry: MediaListEntry,
    onClick: () -> Unit,
    onPlusOne: () -> Unit,
    modifier: Modifier = Modifier
) {
    val media = entry.media
    val total = media.total
    val done = total != null && entry.progress >= total
    Column(
        modifier = modifier
            .width(TsuzukiSizes.inProgressCover.width)
            .clickable(
                onClickLabel = stringResource(com.tobfd.tsuzuki.core.ui.R.string.ui_list_edit_entry),
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
    ) {
        MediaCover(
            imageUrl = media.coverUrl,
            contentDescription = null,
            placeholderColor = coverColorOrNull(media.coverColor),
            badge = stringResource(
                if (media.type == MediaType.ANIME) R.string.home_badge_episode else R.string.home_badge_chapter,
                progressText(entry.progress, total)
            ),
            progress = total?.let { entry.progress.toFloat() / it },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = media.title.userPreferred,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(InProgressTitleHeight)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progressText(entry.progress, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (done) {
                CompletedChip()
            } else if (ListEntryActions.canPlusOne(entry)) {
                PlusOneButton(
                    onClick = onPlusOne,
                    dense = true,
                    completesEntry =
                        total != null && entry.progress + 1 >= total
                )
            }
        }
    }
}

@Composable
private fun CompletedChip() {
    val colors = TsuzukiTheme.statusColors.completed
    Surface(shape = MaterialTheme.shapes.small, color = colors.container, contentColor = colors.onContainer) {
        Text(
            text = stringResource(R.string.home_completed_chip),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = TsuzukiSpacing.small, vertical = TsuzukiSpacing.extraSmall)
        )
    }
}

/** A planned entry: small cover, title, "TV · 12 episodes · Airing", Start. */
@Composable
private fun UpNextRow(entry: MediaListEntry, onClick: () -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val media = entry.media
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
    ) {
        MediaCover(
            imageUrl = media.coverUrl,
            contentDescription = null,
            placeholderColor = coverColorOrNull(media.coverColor),
            modifier = Modifier.width(UpNextCoverWidth)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title.userPreferred,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = upNextMeta(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(onClick = onStart) { Text(stringResource(com.tobfd.tsuzuki.core.ui.R.string.ui_list_start)) }
    }
}

@Composable
private fun upNextMeta(entry: MediaListEntry): String {
    val media = entry.media
    val total = media.total
    return listOfNotNull(
        media.format?.let { stringResource(it.labelRes()) },
        total?.let {
            if (media.type == MediaType.ANIME) {
                pluralStringResource(R.plurals.home_episodes, it, it)
            } else {
                pluralStringResource(R.plurals.home_chapters, it, it)
            }
        },
        media.status?.let { stringResource(it.labelRes()) }
    ).joinToString(" · ")
}
