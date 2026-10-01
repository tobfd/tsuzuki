package com.tobfd.tsuzuki.feature.widgets

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.tobfd.tsuzuki.core.model.FriendActivityFeed
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import com.tobfd.tsuzuki.core.testing.SampleData
import com.tobfd.tsuzuki.feature.widgets.friends.FriendActivityState
import com.tobfd.tsuzuki.feature.widgets.friends.friendActivityState
import com.tobfd.tsuzuki.feature.widgets.inprogress.inProgressItems
import com.tobfd.tsuzuki.feature.widgets.nextepisode.Countdown
import com.tobfd.tsuzuki.feature.widgets.nextepisode.upcomingItems
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLogicTest {

    private val now = Instant.parse("2026-10-01T10:00:00Z")

    @Test
    fun inProgress_isHomesInProgress_newestChangeFirst() {
        val entries = listOf(
            SampleData.listEntry(1, updatedAt = now.minusSeconds(300)),
            SampleData.listEntry(2, status = MediaListStatus.PLANNING, updatedAt = now),
            SampleData.listEntry(3, type = MediaType.MANGA, total = null, updatedAt = now.minusSeconds(60)),
            SampleData.listEntry(4, status = MediaListStatus.REPEATING, updatedAt = now.minusSeconds(900)),
            SampleData.listEntry(5, status = MediaListStatus.COMPLETED, updatedAt = now)
        )

        assertEquals(listOf(3, 1, 4), inProgressItems(entries).map { it.entryId })
    }

    @Test
    fun inProgress_keepsAtMostTenAndNoPlusOneAtTheTotal() {
        val entries = (1..15).map { SampleData.listEntry(it, progress = if (it == 1) 12 else 3, total = 12) }

        val items = inProgressItems(entries)

        assertEquals(WIDGET_MAX_ITEMS, items.size)
        assertFalse(items.first { it.entryId == 1 }.canPlusOne)
        assertTrue(items.first { it.entryId == 2 }.canPlusOne)
        assertTrue(inProgressItems(listOf(SampleData.listEntry(1, progress = 500, total = null))).single().canPlusOne)
    }

    @Test
    fun inProgress_lastEpisodeOpensTheEditorInsteadOfCompleting() {
        val items = inProgressItems(
            listOf(
                SampleData.listEntry(1, progress = 11, total = 12),
                SampleData.listEntry(2, progress = 10, total = 12),
                SampleData.listEntry(3, progress = 500, total = null)
            )
        ).associateBy { it.entryId }

        assertTrue(items.getValue(1).plusOneCompletes)
        assertFalse(items.getValue(2).plusOneCompletes)
        assertFalse(items.getValue(3).plusOneCompletes)
    }

    @Test
    fun upcoming_countsAiredEpisodesNotWatchedYet() {
        val episode = UpcomingEpisode(SampleData.listEntry(1, progress = 3), episode = 6, airingAt = now)

        assertEquals(2, upcomingItems(listOf(episode)).single().behind)
        assertEquals(0, upcomingItems(listOf(episode.copy(episode = 4))).single().behind)
        assertEquals(0, upcomingItems(listOf(episode.copy(episode = 2))).single().behind)
    }

    @Test
    fun countdown_isLiveWithinADay_thenRoundedDays_thenAired() {
        assertEquals(Countdown.Live(Duration.ofHours(5)), Countdown.of(now.plus(Duration.ofHours(5)), now))
        assertEquals(Countdown.Days(1), Countdown.of(now.plus(Duration.ofHours(30)), now))
        assertEquals(Countdown.Days(2), Countdown.of(now.plus(Duration.ofHours(40)), now))
        assertEquals(Countdown.Aired, Countdown.of(now, now))
        assertEquals(Countdown.Aired, Countdown.of(now.minusSeconds(60), now))
    }

    @Test
    fun nextFetch_followsTheAiringButStaysBetweenOneAndTwelveHours() {
        assertEquals(Duration.ofHours(1), WidgetWork.nextFetchDelay(now.plus(Duration.ofMinutes(10)), now))
        assertEquals(
            Duration.ofHours(3).plus(WidgetWork.AFTER_AIRING),
            WidgetWork.nextFetchDelay(now.plus(Duration.ofHours(3)), now)
        )
        assertEquals(Duration.ofHours(12), WidgetWork.nextFetchDelay(now.plus(Duration.ofDays(3)), now))
        assertEquals(Duration.ofHours(12), WidgetWork.nextFetchDelay(null, now))
    }

    @Test
    fun friendActivity_loggedOutShowsTheLoginState() {
        val feed = FriendActivityFeed(emptyList(), fetchedAt = null)

        assertEquals(FriendActivityState.LoggedOut, friendActivityState(loggedIn = false, feed))
        assertEquals(FriendActivityState.Ready(emptyList(), null), friendActivityState(loggedIn = true, feed))
    }

    @Test
    fun listRows_countWholeRowsAndShrinkWithLargeFonts() {
        assertEquals(2, WidgetSizes.listRows(DpSize(400.dp, 251.dp), footer = false))
        assertEquals(1, WidgetSizes.listRows(DpSize(400.dp, 251.dp), footer = false, fontScale = 2f))
        assertEquals(1, WidgetSizes.listRows(DpSize(400.dp, 80.dp), footer = true))
    }

    @Test
    fun layout_followsTheSizeTheLauncherGives() {
        assertEquals(WidgetLayout.Row, WidgetLayout.of(DpSize(250.dp, 60.dp)))
        assertEquals(WidgetLayout.Single, WidgetLayout.of(DpSize(130.dp, 130.dp)))
        assertEquals(WidgetLayout.List, WidgetLayout.of(DpSize(250.dp, 180.dp)))
        assertEquals(WidgetLayout.Row, WidgetLayout.of(DpSize(110.dp, 50.dp)))
        assertEquals(WidgetLayout.Row, WidgetLayout.of(DpSize(400.dp, 120.dp)))
    }
}
