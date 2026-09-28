package com.tobfd.tsuzuki.core.data.notifications

import kotlinx.coroutines.flow.StateFlow

/** Notification data; in M3 only the unread count for the bell badge. */
interface NotificationsRepository {
    /** Unread notifications of the logged-in viewer; 0 until the first refresh. */
    val unreadCount: StateFlow<Int>

    /**
     * Refreshes [unreadCount] with one small query, at most every 5 minutes unless [force] is set
     * (e.g. right after login). Failures keep the last count.
     */
    suspend fun refreshUnreadCount(force: Boolean = false)
}
