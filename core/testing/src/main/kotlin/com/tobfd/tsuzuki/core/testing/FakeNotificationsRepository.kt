package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.notifications.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [NotificationsRepository]; records refresh calls. */
class FakeNotificationsRepository(initialCount: Int = 0) : NotificationsRepository {
    override val unreadCount = MutableStateFlow(initialCount)

    /** The `force` flag of every refresh call, in order. */
    val refreshCalls = mutableListOf<Boolean>()

    override suspend fun refreshUnreadCount(force: Boolean) {
        refreshCalls += force
    }
}
