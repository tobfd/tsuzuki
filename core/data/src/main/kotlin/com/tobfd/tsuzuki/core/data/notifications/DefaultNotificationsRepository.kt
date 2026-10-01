package com.tobfd.tsuzuki.core.data.notifications

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.datastore.AlertStateStore
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.core.network.MarkNotificationsReadQuery
import com.tobfd.tsuzuki.core.network.NotificationsQuery
import com.tobfd.tsuzuki.core.network.UnreadNotificationCountQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val MIN_REFRESH_INTERVAL: Duration = Duration.ofMinutes(5)

/** Loads the next page when the list is this close to its end. */
private const val PREFETCH_DISTANCE = 5

@Singleton
internal class DefaultNotificationsRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val clock: Clock,
    private val alertStateStore: AlertStateStore
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
            response.data?.Viewer?.unreadNotificationCount?.let { setSeenInApp(it) }
        }
    }

    /**
     * A count the app showed itself (badge refresh, reset, mark all read): the Android notifications
     * don't announce what the viewer could already see in the app.
     */
    private suspend fun setSeenInApp(unread: Int) {
        count.value = unread
        alertStateStore.setNotificationsSeen(unread, newestId = null)
    }

    override fun startVisit(): NotificationVisit = NotificationVisit(badgeCountAtOpen = count.value)

    override fun notifications(
        filter: NotificationFilter,
        visit: NotificationVisit
    ): Flow<PagingData<NotificationEntry>> = Pager(
        config = PagingConfig(
            pageSize = NOTIFICATIONS_PAGE_SIZE,
            initialLoadSize = NOTIFICATIONS_PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            NotificationsPagingSource(apolloClient, filter, visit, onReset = { setSeenInApp(0) })
        }
    ).flow

    override suspend fun markAllRead(): Result<Unit> {
        val response = try {
            apolloClient.query(MarkNotificationsReadQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        if (response.data?.Page == null) {
            return Result.failure(response.appErrorOrNull() ?: AppError.Unknown("AniList did not reset the count"))
        }
        setSeenInApp(0)
        return Result.success(Unit)
    }

    override suspend fun fetchUnreadCount(): Result<Int> {
        val response = try {
            apolloClient.query(UnreadNotificationCountQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        val unread = response.data?.Viewer?.unreadNotificationCount
            ?: return Result.failure(response.appErrorOrNull() ?: AppError.Unknown("AniList returned no count"))
        count.value = unread
        return Result.success(unread)
    }

    override suspend fun newestNotifications(): Result<List<Notification>> {
        val query = NotificationsQuery(page = 1, types = Optional.absent(), reset = Optional.present(false))
        val response = try {
            apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        val page = response.data?.Page
            ?: return Result.failure(response.appErrorOrNull() ?: AppError.Unknown("AniList returned no notifications"))
        val entries = page.notifications.orEmpty().mapNotNull { raw ->
            raw?.toModel()?.let { NotificationEntry(it, isUnread = true) }
        }
        return Result.success(groupLikes(entries).map { it.merged().notification })
    }
}
