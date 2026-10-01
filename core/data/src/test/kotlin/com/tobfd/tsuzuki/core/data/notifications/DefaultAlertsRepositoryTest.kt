package com.tobfd.tsuzuki.core.data.notifications

import androidx.datastore.preferences.core.emptyPreferences
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.datastore.AlertStateStore
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.testing.FakeNotificationsRepository
import com.tobfd.tsuzuki.core.testing.InMemoryDataStore
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAlertsRepositoryTest {

    private val notifications = FakeNotificationsRepository()
    private val store = AlertStateStore(InMemoryDataStore(emptyPreferences()))
    private val repository = DefaultAlertsRepository(notifications, store)

    private fun follow(id: Int) =
        Notification.Follow(id, Instant.ofEpochSecond(id.toLong()), UserLite(id, "Fern", null))

    @Test
    fun firstCheck_onlyRemembersTheCount() = runTest {
        notifications.fetchUnreadCountResult = Result.success(4)

        val result = repository.newNotifications()

        assertEquals(emptyList<Notification>(), result.getOrThrow())
        assertEquals(0, notifications.newestCalls)
        assertEquals(4, store.current().knownUnreadCount)
    }

    @Test
    fun unchangedCount_makesOneRequestOnly() = runTest {
        store.setNotificationsSeen(2, newestId = 10)
        notifications.fetchUnreadCountResult = Result.success(2)

        assertEquals(emptyList<Notification>(), repository.newNotifications().getOrThrow())
        assertEquals(1, notifications.fetchUnreadCountCalls)
        assertEquals(0, notifications.newestCalls)
    }

    @Test
    fun risingCount_returnsJustTheNewOnes() = runTest {
        store.setNotificationsSeen(1, newestId = 10)
        notifications.fetchUnreadCountResult = Result.success(3)
        notifications.newestResult = Result.success(listOf(follow(12), follow(11), follow(10), follow(9)))

        val fresh = repository.newNotifications().getOrThrow()

        assertEquals(listOf(12, 11), fresh.map { it.id })
        assertEquals(3, store.current().knownUnreadCount)
        assertEquals(12, store.current().newestNotificationId)
    }

    @Test
    fun alreadyShownNotifications_areNotAnnouncedAgain() = runTest {
        // The viewer read everything (count 0), then two arrived; one of them was shown before.
        store.setNotificationsSeen(0, newestId = 11)
        notifications.fetchUnreadCountResult = Result.success(2)
        notifications.newestResult = Result.success(listOf(follow(12), follow(11)))

        assertEquals(listOf(12), repository.newNotifications().getOrThrow().map { it.id })
    }

    @Test
    fun countTheAppShowedItself_isNotAnnounced() = runTest {
        store.setNotificationsSeen(0, newestId = 5)
        // The badge refresh in the app saw 2 unread notifications.
        store.setNotificationsSeen(2, newestId = null)
        notifications.fetchUnreadCountResult = Result.success(2)

        assertTrue(repository.newNotifications().getOrThrow().isEmpty())
        assertEquals(0, notifications.newestCalls)
    }

    @Test
    fun failedPage_keepsTheOldCount_soTheNextCheckTriesAgain() = runTest {
        store.setNotificationsSeen(1, newestId = 10)
        notifications.fetchUnreadCountResult = Result.success(2)
        notifications.newestResult = Result.failure(AppError.Offline)

        assertTrue(repository.newNotifications().isFailure)
        assertEquals(1, store.current().knownUnreadCount)
    }

    @Test
    fun failedCount_isReported() = runTest {
        notifications.fetchUnreadCountResult = Result.failure(AppError.RateLimited(60))

        assertEquals(AppError.RateLimited(60), repository.newNotifications().exceptionOrNull())
    }

    @Test
    fun clear_forgetsTheSession_butKeepsTheHint() = runTest {
        repository.markPermissionHintShown()
        repository.setEpisodeCheckpoint(Instant.ofEpochSecond(100))
        store.setNotificationsSeen(3, newestId = 7)

        repository.clear()

        assertEquals(null, repository.episodeCheckpoint())
        assertEquals(null, store.current().knownUnreadCount)
        assertTrue(repository.permissionHintShown.first())
        assertFalse(store.current().newestNotificationId != null)
    }
}
