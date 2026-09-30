package com.tobfd.tsuzuki.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaDetailTest {

    private val ratedYear = MediaRanking(3, RankingType.Rated, allTime = false, year = 2023, season = null)
    private val ratedSeason = MediaRanking(1, RankingType.Rated, allTime = false, year = 2023, season = "FALL")
    private val ratedAllTime = MediaRanking(1, RankingType.Rated, allTime = true, year = null, season = null)
    private val popularAllTime = MediaRanking(20, RankingType.Popular, allTime = true, year = null, season = null)

    @Test
    fun headline_prefersBestRatedOfAllTime() {
        assertEquals(ratedAllTime, listOf(popularAllTime, ratedYear, ratedAllTime).headline())
    }

    @Test
    fun headline_thenTheYearNotTheSeason() {
        assertEquals(ratedYear, listOf(ratedSeason, popularAllTime, ratedYear).headline())
    }

    @Test
    fun headline_thenMostPopular() {
        assertEquals(popularAllTime, listOf(ratedSeason, popularAllTime).headline())
        assertNull(listOf(ratedSeason).headline())
    }

    @Test
    fun trailer_linksOnlyYouTube() {
        assertEquals("https://www.youtube.com/watch?v=abc", Trailer("youtube", "abc").url)
        assertNull(Trailer("dailymotion", "x").url)
    }
}
