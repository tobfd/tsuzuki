package com.tobfd.tsuzuki.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSeasonTest {

    @Test
    fun of_countsDecemberToNextYearsWinter() {
        assertEquals(MediaSeason.WINTER to 2027, MediaSeason.of(LocalDate.of(2026, 12, 5)))
        assertEquals(MediaSeason.WINTER to 2026, MediaSeason.of(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun of_mapsTheOtherQuarters() {
        assertEquals(MediaSeason.SPRING to 2026, MediaSeason.of(LocalDate.of(2026, 4, 1)))
        assertEquals(MediaSeason.SUMMER to 2026, MediaSeason.of(LocalDate.of(2026, 8, 31)))
        assertEquals(MediaSeason.FALL to 2026, MediaSeason.of(LocalDate.of(2026, 9, 30)))
    }
}
