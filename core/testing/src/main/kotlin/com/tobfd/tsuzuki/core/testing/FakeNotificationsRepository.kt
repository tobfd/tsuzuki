package com.tobfd.tsuzuki.core.testing

import androidx.paging.PagingData
import com.tobfd.tsuzuki.core.data.notifications.NotificationVisit
import com.tobfd.tsuzuki.core.data.notifications.NotificationsRepository
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** In-memory [NotificationsRepository]; records refresh calls and the filters asked for. */
class FakeNotificationsRepository(initialCount: Int = 0) : NotificationsRepository {
    override val unreadCount = MutableStateFlow(initialCount)

    /** The `force` flag of every refresh call, in order. */
    val refreshCalls = mutableListOf<Boolean>()

    /** What [notifications] returns per filter; empty for filters not set. */
    val pages = mutableMapOf<NotificationFilter, List<NotificationEntry>>()

    /** Every filter [notifications] was called with, in order. */
    val requestedFilters = mutableListOf<NotificationFilter>()

    /** The next [markAllRead] result. */
    var markAllReadResult: Result<Unit> = Result.success(Unit)

    var markAllReadCalls = 0
        private set

    /** The next [fetchUnreadCount] result; by default the current [unreadCount]. */
    var fetchUnreadCountResult: Result<Int>? = null

    /** The next [newestNotifications] result. */
    var newestResult: Result<List<Notification>> = Result.success(emptyList())

    var fetchUnreadCountCalls = 0
        private set

    var newestCalls = 0
        private set

    override suspend fun refreshUnreadCount(force: Boolean) {
        refreshCalls += force
    }

    override fun startVisit(): NotificationVisit = NotificationVisit(unreadCount.value)

    override fun notifications(
        filter: NotificationFilter,
        visit: NotificationVisit
    ): Flow<PagingData<NotificationEntry>> {
        requestedFilters += filter
        return flowOf(PagingData.from(pages[filter].orEmpty()))
    }

    override suspend fun markAllRead(): Result<Unit> {
        markAllReadCalls++
        return markAllReadResult.onSuccess { unreadCount.value = 0 }
    }

    override suspend fun fetchUnreadCount(): Result<Int> {
        fetchUnreadCountCalls++
        return (fetchUnreadCountResult ?: Result.success(unreadCount.value)).onSuccess { unreadCount.value = it }
    }

    override suspend fun newestNotifications(): Result<List<Notification>> {
        newestCalls++
        return newestResult
    }
}
