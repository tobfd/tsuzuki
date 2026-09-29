package com.tobfd.tsuzuki.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreFormatTest {

    @Test
    fun fromRaw_showsTheScoreInEachFormat() {
        assertEquals(85.0, ScoreFormat.POINT_100.fromRaw(85), 0.0)
        assertEquals(8.5, ScoreFormat.POINT_10_DECIMAL.fromRaw(85), 0.0)
        assertEquals(9.0, ScoreFormat.POINT_10.fromRaw(85), 0.0)
        assertEquals(4.0, ScoreFormat.POINT_5.fromRaw(85), 0.0)
        assertEquals(3.0, ScoreFormat.POINT_3.fromRaw(85), 0.0)
    }

    @Test
    fun fromRaw_keepsZeroAsNotScored() {
        ScoreFormat.entries.forEach { assertEquals(it.name, 0.0, it.fromRaw(0), 0.0) }
    }

    @Test
    fun fromRaw_point3_usesAniListsBands() {
        assertEquals(1.0, ScoreFormat.POINT_3.fromRaw(35), 0.0)
        assertEquals(2.0, ScoreFormat.POINT_3.fromRaw(36), 0.0)
        assertEquals(2.0, ScoreFormat.POINT_3.fromRaw(60), 0.0)
        assertEquals(3.0, ScoreFormat.POINT_3.fromRaw(61), 0.0)
    }

    @Test
    fun toRaw_convertsBackToHundredPoints() {
        assertEquals(85, ScoreFormat.POINT_100.toRaw(85.0))
        assertEquals(85, ScoreFormat.POINT_10_DECIMAL.toRaw(8.5))
        assertEquals(80, ScoreFormat.POINT_10.toRaw(8.0))
        assertEquals(80, ScoreFormat.POINT_5.toRaw(4.0))
        assertEquals(35, ScoreFormat.POINT_3.toRaw(1.0))
        assertEquals(60, ScoreFormat.POINT_3.toRaw(2.0))
        assertEquals(85, ScoreFormat.POINT_3.toRaw(3.0))
        assertEquals(0, ScoreFormat.POINT_5.toRaw(0.0))
    }

    @Test
    fun scoresSetInTheViewersFormat_roundTrip() {
        val samples = mapOf(
            ScoreFormat.POINT_100 to listOf(1.0, 50.0, 99.0),
            ScoreFormat.POINT_10_DECIMAL to listOf(0.5, 7.5, 10.0),
            ScoreFormat.POINT_10 to listOf(1.0, 6.0, 10.0),
            ScoreFormat.POINT_5 to listOf(1.0, 3.0, 5.0),
            ScoreFormat.POINT_3 to listOf(1.0, 2.0, 3.0)
        )
        samples.forEach { (format, scores) ->
            scores.forEach { score -> assertEquals("$format $score", score, format.fromRaw(format.toRaw(score)), 0.0) }
        }
    }
}
