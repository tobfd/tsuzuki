package com.tobfd.tsuzuki.core.ui

import com.tobfd.tsuzuki.core.model.ActivityDay
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityHeatmapTest {

    // A Wednesday.
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun weeks_withoutHistory_coverTwelveWeeksFromMondayWithTheFutureEmpty() {
        val weeks = heatmapWeeks(emptyList(), today)

        assertEquals(HEATMAP_MIN_WEEKS, weeks.size)
        assertEquals(DayOfWeek.MONDAY, weeks.first().first()!!.first.dayOfWeek)
        assertEquals(LocalDate.of(2026, 7, 13), weeks.first().first()!!.first)
        assertEquals(today to 0, weeks.last()[2])
        // Thursday to Sunday of this week haven't happened yet.
        assertNull(weeks.last()[3])
        assertNull(weeks.last()[6])
    }

    @Test
    fun levels_areRelativeToTheBusiestDay() {
        val days = listOf(
            ActivityDay(today, 8),
            ActivityDay(today.minusDays(1), 1),
            ActivityDay(today.minusDays(2), 4),
            // Outside the year the grid can show.
            ActivityDay(today.minusYears(2), 50)
        )

        val levels = heatmapWeeks(days, today).flatten().filterNotNull().toMap()

        assertEquals(4, levels[today])
        assertEquals(1, levels[today.minusDays(1)])
        assertEquals(2, levels[today.minusDays(2)])
        assertEquals(0, levels[today.minusDays(3)])
    }

    @Test
    fun weekCount_startsAtTheOldestActiveDayAndStaysWithinAYear() {
        // AniList keeps about half a year: 185 days back from today.
        val halfYear = listOf(ActivityDay(today.minusDays(184), 1), ActivityDay(today, 2))
        assertEquals(27, heatmapWeekCount(halfYear, today))

        val recent = listOf(ActivityDay(today.minusDays(3), 1))
        assertEquals(HEATMAP_MIN_WEEKS, heatmapWeekCount(recent, today))

        val old = listOf(ActivityDay(today.minusYears(2), 1))
        assertEquals(HEATMAP_MAX_WEEKS, heatmapWeekCount(old, today))

        // Days without activity don't stretch the grid.
        val zero = listOf(ActivityDay(today.minusDays(300), 0), ActivityDay(today, 1))
        assertEquals(HEATMAP_MIN_WEEKS, heatmapWeekCount(zero, today))
    }

    @Test
    fun monthLabels_skipAMonthCutShortAtTheStart() {
        // 2026-03-30 is a Monday: the first week is March for two days only, April starts next week.
        val weeks = heatmapWeeks(listOf(ActivityDay(LocalDate.of(2026, 3, 30), 1)), today)
        val firstDates = monthLabelWeeks(weeks).sorted().map { weeks[it].first()!!.first }

        assertEquals(LocalDate.of(2026, 4, 6), firstDates.first())
        assertEquals(6, firstDates.size)
    }
}
