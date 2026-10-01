package com.tobfd.tsuzuki.feature.notifications

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.ListActivitySummary
import com.tobfd.tsuzuki.core.model.MediaNotificationKind
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationEntry
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.testing.FakeNotificationsRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val tobfd = UserLite(1, "tobfd", null)

private fun follow(id: Int, at: String) = NotificationEntry(Notification.Follow(id, Instant.parse(at), tobfd), false)

class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNotificationsRepository(initialCount = 3)

    // A Wednesday.
    private val clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.viewModel(): NotificationsViewModel =
        NotificationsViewModel(repository, clock).also { viewModel ->
            backgroundScope.launch { viewModel.notifications(NotificationFilter.All).collect {} }
            runCurrent()
        }

    @Test
    fun opening_loadsAll_andAFilterLoadsOnlyWhenItsPageShows() = runTest {
        val viewModel = viewModel()
        assertEquals(listOf(NotificationFilter.All), repository.requestedFilters)

        viewModel.onFilterChange(NotificationFilter.Airing)
        backgroundScope.launch { viewModel.notifications(NotificationFilter.Airing).collect {} }
        runCurrent()

        assertEquals(listOf(NotificationFilter.All, NotificationFilter.Airing), repository.requestedFilters)
        assertEquals(NotificationFilter.Airing, viewModel.uiState.value.filter)
    }

    @Test
    fun swipingBack_reusesTheFilterThatWasShown() = runTest {
        val viewModel = viewModel()

        val again = viewModel.notifications(NotificationFilter.All)

        assertTrue(again === viewModel.notifications(NotificationFilter.All))
    }

    @Test
    fun markAllRead_clearsTheHighlightAndTheBadge() = runTest {
        val viewModel = viewModel()

        viewModel.onMarkAllRead()
        runCurrent()

        assertTrue(viewModel.uiState.value.allRead)
        assertFalse(viewModel.uiState.value.markingAllRead)
        assertEquals(0, repository.unreadCount.value)
    }

    @Test
    fun markAllRead_whenOffline_keepsTheHighlightAndReportsTheError() = runTest {
        repository.markAllReadResult = Result.failure(AppError.Offline)
        val viewModel = viewModel()

        viewModel.eventFlow.test {
            viewModel.onMarkAllRead()
            assertEquals(NotificationsEvent.MarkAllReadFailed(AppError.Offline), awaitItem())
        }
        assertFalse(viewModel.uiState.value.allRead)
        assertEquals(3, repository.unreadCount.value)
    }

    @Test
    fun rows_areGroupedByWeek() = runTest {
        val entries = listOf(
            follow(1, "2026-09-30T08:00:00Z"),
            follow(2, "2026-09-28T08:00:00Z"),
            follow(3, "2026-09-27T08:00:00Z"),
            follow(4, "2026-09-20T08:00:00Z")
        )

        val items = flowOf(PagingData.from(entries))
            .map { it.withSections(LocalDate.of(2026, 9, 30), ZoneOffset.UTC, DayOfWeek.MONDAY) }
            .asSnapshot()

        assertEquals(
            listOf(
                NotificationListItem.Header(NotificationSection.ThisWeek),
                NotificationListItem.Row(entries[0]),
                NotificationListItem.Row(entries[1]),
                NotificationListItem.Header(NotificationSection.LastWeek),
                NotificationListItem.Row(entries[2]),
                NotificationListItem.Header(NotificationSection.Earlier),
                NotificationListItem.Row(entries[3])
            ),
            items
        )
    }

    @Test
    fun tapping_opensTheMediaOrTheUser() {
        val opened = mutableListOf<String>()
        val onMedia: (Int) -> Unit = { opened += "media $it" }
        val onUser: (Int, String) -> Unit = { id, _ -> opened += "user $id" }
        val now = Instant.parse("2026-09-30T08:00:00Z")
        val summary = ListActivitySummary("watched episode", "18", 154587, "Frieren")

        Notification.Airing(1, now, 18, SampleData.frieren.media).open(onMedia, onUser)
        Notification.Follow(2, now, tobfd).open(onMedia, onUser)
        Notification.ActivityEvent(
            3,
            now,
            ActivityNotificationKind.Like,
            listOf(tobfd),
            9,
            summary
        ).open(onMedia, onUser)
        Notification.ActivityEvent(
            4,
            now,
            ActivityNotificationKind.Mention,
            listOf(tobfd),
            9,
            null
        ).open(onMedia, onUser)
        Notification.MediaEvent(5, now, MediaNotificationKind.Deletion, null, listOf("Old"), null).open(onMedia, onUser)
        // AniList leaves the media out of the liked list update: open the user instead.
        val untitled = ListActivitySummary("watched episode", "2 - 16", null, null)
        Notification.ActivityEvent(
            6,
            now,
            ActivityNotificationKind.Like,
            listOf(tobfd),
            9,
            untitled
        ).open(onMedia, onUser)

        assertEquals(listOf("media 154587", "user 1", "media 154587", "user 1", "user 1"), opened)
    }
}
