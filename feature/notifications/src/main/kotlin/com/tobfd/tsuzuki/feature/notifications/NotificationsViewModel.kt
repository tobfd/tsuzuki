package com.tobfd.tsuzuki.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.notifications.NotificationsRepository
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.feature.notifications.alerts.AlertPoster
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The time groups of the list (docs/DESIGN.md, Notifications). */
enum class NotificationSection {
    ThisWeek,
    LastWeek,
    Earlier
}

/** A row of the notifications list: a time group's header or a notification. */
sealed interface NotificationListItem {
    data class Header(val section: NotificationSection) : NotificationListItem

    data class Row(val entry: NotificationEntry) : NotificationListItem
}

data class NotificationsUiState(
    val filter: NotificationFilter = NotificationFilter.All,
    /** Set after "Mark all as read": nothing is highlighted any more. */
    val allRead: Boolean = false,
    val markingAllRead: Boolean = false
)

sealed interface NotificationsEvent {
    data class MarkAllReadFailed(val error: AppError) : NotificationsEvent
}

/**
 * The notifications screen (docs/ROADMAP.md, M10): one request per page of each filter that is shown. Opening the
 * screen resets the badge; the notifications that were unread at that moment stay highlighted until the
 * screen closes or "Mark all as read" is tapped.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val clock: Clock,
    alertPoster: AlertPoster
) : ViewModel() {

    private val visit = repository.startVisit()

    init {
        // The screen shows them now: their Android notifications can go.
        alertPoster.cancelAniList()
    }
    private val state = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = state.asStateFlow()

    private val events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<NotificationsEvent> = events.receiveAsFlow()

    private val pages = mutableMapOf<NotificationFilter, Flow<PagingData<NotificationListItem>>>()

    /**
     * The notifications of [filter], one request per page. Each filter's page of the swipeable list asks
     * for its flow when it first shows, so filters nobody swipes to load nothing; a flow is kept for the
     * visit, so swiping back doesn't load again.
     */
    fun notifications(filter: NotificationFilter): Flow<PagingData<NotificationListItem>> = pages.getOrPut(filter) {
        repository.notifications(filter, visit)
            .map { page -> page.withSections(today(), clock.zone) }
            .cachedIn(viewModelScope)
    }

    fun onFilterChange(value: NotificationFilter) {
        state.update { it.copy(filter = value) }
    }

    fun onMarkAllRead() {
        if (state.value.markingAllRead) return
        state.update { it.copy(markingAllRead = true) }
        viewModelScope.launch {
            repository.markAllRead()
                .onSuccess { state.update { it.copy(allRead = true, markingAllRead = false) } }
                .onFailure { error ->
                    state.update { it.copy(markingAllRead = false) }
                    events.send(NotificationsEvent.MarkAllReadFailed(error.toAppError()))
                }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}

/** Rows of [this] page with a header before each time group. */
internal fun PagingData<NotificationEntry>.withSections(
    today: LocalDate,
    zone: ZoneId,
    firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
): PagingData<NotificationListItem> = map<NotificationEntry, NotificationListItem> { NotificationListItem.Row(it) }
    .insertSeparators { before, after ->
        val next = (after as? NotificationListItem.Row)?.entry ?: return@insertSeparators null
        val section = sectionOf(next.notification.createdAt, today, zone, firstDayOfWeek)
        val previous = (before as? NotificationListItem.Row)?.entry
            ?.let { sectionOf(it.notification.createdAt, today, zone, firstDayOfWeek) }
        if (section != previous) NotificationListItem.Header(section) else null
    }

/** "This week" counts from the locale's first day of the current week. */
internal fun sectionOf(
    createdAt: Instant,
    today: LocalDate,
    zone: ZoneId,
    firstDayOfWeek: DayOfWeek
): NotificationSection {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val date = createdAt.atZone(zone).toLocalDate()
    return when {
        !date.isBefore(weekStart) -> NotificationSection.ThisWeek
        !date.isBefore(weekStart.minusWeeks(1)) -> NotificationSection.LastWeek
        else -> NotificationSection.Earlier
    }
}

private fun Throwable.toAppError(): AppError = this as? AppError ?: AppError.Unknown(message)
