package com.tobfd.tsuzuki.core.ui.score

import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.ui.score.ScoreDisplay.Sentiment
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreDisplayTest {

    private fun display(score: Double, format: ScoreFormat, locale: Locale = Locale.ENGLISH) =
        scoreDisplay(score, format, locale)

    @Test
    fun zeroScore_isNotScoredInEveryFormat() {
        ScoreFormat.entries.forEach { format ->
            assertEquals(format.name, ScoreDisplay.None, display(0.0, format))
        }
    }

    @Test
    fun point100_showsWholeNumberOutOf100() {
        assertEquals(ScoreDisplay.Number("85", 100), display(85.0, ScoreFormat.POINT_100))
    }

    @Test
    fun point10Decimal_showsOneDecimal() {
        assertEquals(ScoreDisplay.Number("8.5", 10), display(8.5, ScoreFormat.POINT_10_DECIMAL))
        assertEquals(ScoreDisplay.Number("8.0", 10), display(8.0, ScoreFormat.POINT_10_DECIMAL))
    }

    @Test
    fun point10Decimal_usesLocaleDecimalSeparator() {
        assertEquals(ScoreDisplay.Number("8,5", 10), display(8.5, ScoreFormat.POINT_10_DECIMAL, Locale.GERMAN))
    }

    @Test
    fun point10Decimal_roundsToTenthsWithoutFloatNoise() {
        assertEquals(ScoreDisplay.Number("7.3", 10), display(7.2999999, ScoreFormat.POINT_10_DECIMAL))
    }

    @Test
    fun point10_showsWholeNumber() {
        assertEquals(ScoreDisplay.Number("8", 10), display(8.0, ScoreFormat.POINT_10))
    }

    @Test
    fun point5_showsFilledStars() {
        assertEquals(ScoreDisplay.Stars(4), display(4.0, ScoreFormat.POINT_5))
    }

    @Test
    fun point5_neverExceedsFiveStars() {
        assertEquals(ScoreDisplay.Stars(5), display(7.0, ScoreFormat.POINT_5))
    }

    @Test
    fun point3_mapsOneTwoThreeToSmileys() {
        assertEquals(ScoreDisplay.Smiley(Sentiment.Disliked), display(1.0, ScoreFormat.POINT_3))
        assertEquals(ScoreDisplay.Smiley(Sentiment.Neutral), display(2.0, ScoreFormat.POINT_3))
        assertEquals(ScoreDisplay.Smiley(Sentiment.Liked), display(3.0, ScoreFormat.POINT_3))
    }

    @Test
    fun scoresAboveTheScale_areCappedAtTheMaximum() {
        assertEquals(ScoreDisplay.Number("100", 100), display(120.0, ScoreFormat.POINT_100))
        assertEquals(ScoreDisplay.Number("10", 10), display(11.0, ScoreFormat.POINT_10))
        assertEquals(ScoreDisplay.Number("10.0", 10), display(10.4, ScoreFormat.POINT_10_DECIMAL))
    }
}
