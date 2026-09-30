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
    fun weeks_coverTwelveWeeksFromMondayWithTheFutureEmpty() {
        val weeks = heatmapWeeks(emptyList(), today)

        assertEquals(HEATMAP_WEEKS, weeks.size)
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
            ActivityDay(today.minusYears(1), 50)
        )

        val levels = heatmapWeeks(days, today).flatten().filterNotNull().toMap()

        assertEquals(4, levels[today])
        assertEquals(1, levels[today.minusDays(1)])
        assertEquals(2, levels[today.minusDays(2)])
        assertEquals(0, levels[today.minusDays(3)])
    }
}
