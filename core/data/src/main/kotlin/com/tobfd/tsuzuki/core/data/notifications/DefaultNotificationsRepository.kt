package com.tobfd.tsuzuki.core.data.notifications

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.network.UnreadNotificationCountQuery
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val MIN_REFRESH_INTERVAL: Duration = Duration.ofMinutes(5)

@Singleton
internal class DefaultNotificationsRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val clock: Clock
) : NotificationsRepository {
    private val count = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = count.asStateFlow()

    private val mutex = Mutex()
    private var lastAttempt: Instant? = null

    override suspend fun refreshUnreadCount(force: Boolean) {
        mutex.withLock {
            val now = clock.instant()
            val last = lastAttempt
            if (!force && last != null && Duration.between(last, now) < MIN_REFRESH_INTERVAL) return
            lastAttempt = now

            val response = try {
                apolloClient.query(UnreadNotificationCountQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
            } catch (e: ApolloException) {
                return
            }
            response.data?.Viewer?.unreadNotificationCount?.let { count.value = it }
        }
    }
}
