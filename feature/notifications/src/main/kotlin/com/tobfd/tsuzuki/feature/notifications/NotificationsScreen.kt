package com.tobfd.tsuzuki.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.R as DesignR
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiPullToRefresh
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.centeredMaxWidth
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.ListActivitySummary
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.ui.NotificationRow
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.message
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

/** The viewer's notifications, opened from the bell. */
@Serializable
data object NotificationsRoute : NavKey

/** Notifications (docs/DESIGN.md, Notifications). Opening the screen resets the bell's badge. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenUser: (id: Int, name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<NotificationsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notifications = viewModel.notifications.collectAsLazyPagingItems()
    NotificationsContent(
        state = state,
        notifications = notifications,
        events = viewModel.eventFlow,
        onBack = onBack,
        onFilterChange = viewModel::onFilterChange,
        onMarkAllRead = viewModel::onMarkAllRead,
        onOpenNotification = { notification -> notification.open(onOpenMedia, onOpenUser) },
        modifier = modifier
    )
}

/** Airing and media changes open the media, follows the user, activity notifications the media of a list update, else the user. */
internal fun Notification.open(onOpenMedia: (Int) -> Unit, onOpenUser: (Int, String) -> Unit) {
    when (this) {
        is Notification.Airing -> onOpenMedia(media.id)

        is Notification.Follow -> onOpenUser(user.id, user.name)

        is Notification.ActivityEvent -> {
            val summary = listActivity
            val user = users.first()
            if (summary != null && kind != ActivityNotificationKind.Message) {
                onOpenMedia(summary.mediaId)
            } else {
                onOpenUser(user.id, user.name)
            }
        }

        is Notification.MediaEvent -> media?.let { onOpenMedia(it.id) }
    }
}

@Composable
internal fun NotificationsContent(
    state: NotificationsUiState,
    notifications: LazyPagingItems<NotificationListItem>,
    events: Flow<NotificationsEvent>,
    onBack: () -> Unit,
    onFilterChange: (NotificationFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenNotification: (Notification) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is NotificationsEvent.MarkAllReadFailed -> failure = event.error
            }
        }
    }
    failure?.let { error ->
        val message = error.message()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            failure = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TsuzukiBackTopBar(
                title = stringResource(R.string.notifications_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onMarkAllRead, enabled = !state.markingAllRead) {
                        Icon(
                            painter = painterResource(TsuzukiIcons.DoneAll),
                            contentDescription = stringResource(R.string.notifications_mark_all_read)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FilterChips(selected = state.filter, onSelect = onFilterChange)
            val refresh = notifications.loadState.refresh
            TsuzukiPullToRefresh(
                isRefreshing = refresh is LoadState.Loading && notifications.itemCount > 0,
                onRefresh = notifications::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                NotificationList(
                    notifications = notifications,
                    filter = state.filter,
                    allRead = state.allRead,
                    onOpenNotification = onOpenNotification
                )
            }
        }
    }
}

@Composable
private fun FilterChips(selected: NotificationFilter, onSelect: (NotificationFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = TsuzukiSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
    ) {
        items(NotificationFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) }
            )
        }
    }
}

@Composable
private fun NotificationList(
    notifications: LazyPagingItems<NotificationListItem>,
    filter: NotificationFilter,
    allRead: Boolean,
    onOpenNotification: (Notification) -> Unit
) {
    val refresh = notifications.loadState.refresh
    when {
        refresh is LoadState.Loading && notifications.itemCount == 0 -> CenteredProgress()

        refresh is LoadState.Error && notifications.itemCount == 0 -> ErrorState(
            title = stringResource(R.string.notifications_error_title),
            message = refresh.error.toAppError().message(),
            onRetry = notifications::retry,
            modifier = Modifier.fillMaxSize()
        )

        refresh is LoadState.NotLoading && notifications.itemCount == 0 -> EmptyState(
            icon = painterResource(TsuzukiIcons.Notifications),
            title = stringResource(R.string.notifications_empty_title),
            message = stringResource(filter.emptyMessageRes()),
            modifier = Modifier.fillMaxSize()
        )

        // One list per filter, so switching chips starts at the top.
        else -> androidx.compose.runtime.key(filter) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = TsuzukiSpacing.large)
            ) {
                items(
                    count = notifications.itemCount,
                    key = notifications.itemKey { it.key() },
                    contentType = notifications.itemContentType { it::class.simpleName }
                ) { index ->
                    when (val item = notifications[index]) {
                        is NotificationListItem.Header -> SectionTitle(item.section)

                        is NotificationListItem.Row -> {
                            val entry = if (allRead) item.entry.copy(isUnread = false) else item.entry
                            NotificationRow(
                                entry = entry,
                                onClick = { onOpenNotification(entry.notification) },
                                modifier = Modifier.centeredMaxWidth()
                            )
                        }

                        null -> Unit
                    }
                }
                when (val append = notifications.loadState.append) {
                    is LoadState.Loading -> item(key = "appendLoading") { CenteredProgress(Modifier.fillMaxWidth()) }

                    is LoadState.Error -> item(key = "appendError") {
                        AppendError(error = append.error, onRetry = notifications::retry)
                    }

                    is LoadState.NotLoading -> Unit
                }
            }
        }
    }
}

private fun NotificationListItem.key(): String = when (this) {
    is NotificationListItem.Header -> "header-${section.name}"
    is NotificationListItem.Row -> "notification-${entry.notification.id}"
}

@Composable
private fun SectionTitle(section: NotificationSection) {
    Text(
        text = stringResource(section.labelRes()),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .centeredMaxWidth()
            .fillMaxWidth()
            .padding(
                start = TsuzukiSpacing.screenMargin,
                end = TsuzukiSpacing.screenMargin,
                top = TsuzukiSpacing.large,
                bottom = TsuzukiSpacing.small
            )
            .semantics { heading() }
    )
}

@Composable
private fun AppendError(error: Throwable, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TsuzukiSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.notifications_more_failed), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = error.toAppError().message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRetry) { Text(stringResource(DesignR.string.designsystem_retry)) }
    }
}

@Composable
private fun CenteredProgress(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier.padding(TsuzukiSpacing.extraLarge), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unknown(message)

internal fun NotificationFilter.labelRes(): Int = when (this) {
    NotificationFilter.All -> R.string.notifications_filter_all
    NotificationFilter.Airing -> R.string.notifications_filter_airing
    NotificationFilter.Activity -> R.string.notifications_filter_activity
    NotificationFilter.Follows -> R.string.notifications_filter_follows
    NotificationFilter.Media -> R.string.notifications_filter_media
}

private fun NotificationFilter.emptyMessageRes(): Int = when (this) {
    NotificationFilter.All -> R.string.notifications_empty_all
    NotificationFilter.Airing -> R.string.notifications_empty_airing
    NotificationFilter.Activity -> R.string.notifications_empty_activity
    NotificationFilter.Follows -> R.string.notifications_empty_follows
    NotificationFilter.Media -> R.string.notifications_empty_media
}

private fun NotificationSection.labelRes(): Int = when (this) {
    NotificationSection.ThisWeek -> R.string.notifications_section_this_week
    NotificationSection.LastWeek -> R.string.notifications_section_last_week
    NotificationSection.Earlier -> R.string.notifications_section_earlier
}

@ThemePreviews
@Composable
private fun NotificationsContentPreview() {
    val now = Instant.now()
    val users = listOf(UserLite(1, "KiichiVS", null), UserLite(2, "Mathou", null), UserLite(3, "GeckoTV", null))
    val items = listOf(
        NotificationListItem.Header(NotificationSection.ThisWeek),
        NotificationListItem.Row(
            NotificationEntry(
                Notification.ActivityEvent(
                    1,
                    now.minusSeconds(900),
                    ActivityNotificationKind.Like,
                    users,
                    7,
                    ListActivitySummary("watched episode", "17 - 18", 154587, "Frieren")
                ),
                isUnread = true
            )
        ),
        NotificationListItem.Row(
            NotificationEntry(
                Notification.Airing(2, now.minusSeconds(7_200), 18, PreviewListEntries.frieren.media),
                false
            )
        ),
        NotificationListItem.Header(NotificationSection.Earlier),
        NotificationListItem.Row(
            NotificationEntry(Notification.Follow(3, now.minusSeconds(1_900_000), users[2]), false)
        )
    )
    TsuzukiPreview {
        NotificationsContent(
            state = NotificationsUiState(),
            notifications = flowOf(PagingData.from(items)).collectAsLazyPagingItems(),
            events = emptyFlow(),
            onBack = {},
            onFilterChange = {},
            onMarkAllRead = {},
            onOpenNotification = {}
        )
    }
}

@ThemePreviews
@Composable
private fun NotificationsEmptyPreview() {
    TsuzukiPreview {
        NotificationsContent(
            state = NotificationsUiState(filter = NotificationFilter.Airing),
            notifications = flowOf(PagingData.empty<NotificationListItem>()).collectAsLazyPagingItems(),
            events = emptyFlow(),
            onBack = {},
            onFilterChange = {},
            onMarkAllRead = {},
            onOpenNotification = {}
        )
    }
}
