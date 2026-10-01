package com.tobfd.tsuzuki.core.data.notifications

import androidx.datastore.preferences.core.emptyPreferences
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.exception.ApolloNetworkException
import com.benasher44.uuid.uuid4
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.datastore.AlertStateStore
import com.tobfd.tsuzuki.core.network.UnreadNotificationCountQuery
import com.tobfd.tsuzuki.core.testing.InMemoryDataStore
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNotificationsRepositoryTest {

    private val apollo = TestApollo()
    private val clock = MutableClock(Instant.parse("2026-09-28T12:00:00Z"))
    private val alertStateStore = AlertStateStore(InMemoryDataStore(emptyPreferences()))
    private val repository = DefaultNotificationsRepository(apollo.client, clock, alertStateStore)

    @After
    fun tearDown() {
        apollo.client.close()
    }

    private fun enqueueCount(count: Int) {
        val data = UnreadNotificationCountQuery.Data(
            Viewer = UnreadNotificationCountQuery.Viewer(__typename = "User", id = 1, unreadNotificationCount = count)
        )
        apollo.queue.enqueue(ApolloResponse.Builder(UnreadNotificationCountQuery(), uuid4()).data(data).build())
    }

    @Test
    fun countIsZero_beforeTheFirstRefresh() {
        assertEquals(0, repository.unreadCount.value)
    }

    @Test
    fun refresh_readsTheCount() = runTest {
        enqueueCount(3)
        repository.refreshUnreadCount()
        assertEquals(3, repository.unreadCount.value)
    }

    @Test
    fun refresh_tellsTheAndroidNotificationsWhatTheAppShowed() = runTest {
        enqueueCount(3)
        repository.refreshUnreadCount()
        assertEquals(3, alertStateStore.current().knownUnreadCount)
    }

    @Test
    fun fetchUnreadCount_updatesTheBadge_andReportsFailures() = runTest {
        enqueueCount(5)
        assertEquals(5, repository.fetchUnreadCount().getOrThrow())
        assertEquals(5, repository.unreadCount.value)

        apollo.queue.enqueue(
            ApolloResponse.Builder(
                UnreadNotificationCountQuery(),
                uuid4()
            ).exception(ApolloNetworkException("offline")).build()
        )
        assertTrue(repository.fetchUnreadCount().isFailure)
    }

    @Test
    fun secondRefreshWithinFiveMinutes_makesNoRequest() = runTest {
        enqueueCount(3)
        repository.refreshUnreadCount()
        clock.advanceBy(Duration.ofMinutes(4))

        repository.refreshUnreadCount()

        assertEquals(1, apollo.requests)
    }

    @Test
    fun refreshAfterFiveMinutes_asksAgain() = runTest {
        enqueueCount(3)
        repository.refreshUnreadCount()
        clock.advanceBy(Duration.ofMinutes(5))
        enqueueCount(7)

        repository.refreshUnreadCount()

        assertEquals(2, apollo.requests)
        assertEquals(7, repository.unreadCount.value)
    }

    @Test
    fun forcedRefresh_ignoresTheInterval() = runTest {
        enqueueCount(3)
        repository.refreshUnreadCount()
        enqueueCount(0)

        repository.refreshUnreadCount(force = true)

        assertEquals(2, apollo.requests)
        assertEquals(0, repository.unreadCount.value)
    }

    @Test
    fun failedRefresh_keepsTheLastCount() = runTest {
        enqueueCount(3)
        repository.refreshUnreadCount()
        apollo.queue.enqueueNetworkError()

        repository.refreshUnreadCount(force = true)

        assertEquals(3, repository.unreadCount.value)
    }
}
