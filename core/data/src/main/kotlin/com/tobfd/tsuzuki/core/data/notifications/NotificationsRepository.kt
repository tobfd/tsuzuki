package com.tobfd.tsuzuki.core.data.notifications

import androidx.paging.PagingData
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The viewer's notifications (docs/ROADMAP.md, M10) and the unread count for the bell badge. */
interface NotificationsRepository {
    /** Unread notifications of the logged-in viewer; 0 until the first refresh. */
    val unreadCount: StateFlow<Int>

    /**
     * Refreshes [unreadCount] with one small query, at most every 5 minutes unless [force] is set
     * (e.g. right after login). Failures keep the last count.
     */
    suspend fun refreshUnreadCount(force: Boolean = false)

    /** Starts a visit to the notifications screen; the newest [unreadCount] notifications count as unread. */
    fun startVisit(): NotificationVisit

    /**
     * Notifications of [filter], one request per page of 25. The first page of the [visit] resets the unread
     * count on AniList and sets [unreadCount] to 0. Consecutive likes on the same activity come as one entry.
     */
    fun notifications(filter: NotificationFilter, visit: NotificationVisit): Flow<PagingData<NotificationEntry>>

    /** "Mark all as read": resets the unread count on AniList (one request) and sets [unreadCount] to 0. */
    suspend fun markAllRead(): Result<Unit>
}
