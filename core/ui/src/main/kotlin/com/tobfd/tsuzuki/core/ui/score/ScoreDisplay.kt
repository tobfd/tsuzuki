package com.tobfd.tsuzuki.core.ui.score

import com.tobfd.tsuzuki.core.model.ScoreFormat
import java.util.Locale
import kotlin.math.roundToInt

/** How a score is shown, decided independently of rendering so it can be unit tested. */
sealed interface ScoreDisplay {
    /** Not scored (AniList uses 0); shown as a dash. */
    data object None : ScoreDisplay

    /** Numeric score such as "85" or "8.5" on a scale up to [max]. */
    data class Number(val text: String, val max: Int) : ScoreDisplay

    /** [filled] of [MAX_STARS] stars. */
    data class Stars(val filled: Int) : ScoreDisplay

    data class Smiley(val sentiment: Sentiment) : ScoreDisplay

    enum class Sentiment {
        Disliked,
        Neutral,
        Liked
    }

    companion object {
        const val MAX_STARS = 5
    }
}

private const val MAX_POINT_100 = 100
private const val MAX_POINT_10 = 10

/**
 * Maps a list [score], already in the viewer's [format] as AniList returns it, to what the UI shows.
 * [locale] picks the decimal separator for [ScoreFormat.POINT_10_DECIMAL].
 */
fun scoreDisplay(score: Double, format: ScoreFormat, locale: Locale): ScoreDisplay {
    val rounded = score.roundToInt()
    return when (format) {
        ScoreFormat.POINT_100 -> number(rounded.coerceAtMost(MAX_POINT_100), MAX_POINT_100)

        ScoreFormat.POINT_10 -> number(rounded.coerceAtMost(MAX_POINT_10), MAX_POINT_10)

        ScoreFormat.POINT_10_DECIMAL -> {
            val tenths = (score * 10).roundToInt().coerceAtMost(MAX_POINT_10 * 10)
            if (tenths <= 0) {
                ScoreDisplay.None
            } else {
                ScoreDisplay.Number(String.format(locale, "%.1f", tenths / 10.0), MAX_POINT_10)
            }
        }

        ScoreFormat.POINT_5 -> if (rounded <= 0) {
            ScoreDisplay.None
        } else {
            ScoreDisplay.Stars(rounded.coerceAtMost(ScoreDisplay.MAX_STARS))
        }

        ScoreFormat.POINT_3 -> when {
            rounded <= 0 -> ScoreDisplay.None
            rounded == 1 -> ScoreDisplay.Smiley(ScoreDisplay.Sentiment.Disliked)
            rounded == 2 -> ScoreDisplay.Smiley(ScoreDisplay.Sentiment.Neutral)
            else -> ScoreDisplay.Smiley(ScoreDisplay.Sentiment.Liked)
        }
    }
}

private fun number(value: Int, max: Int): ScoreDisplay =
    if (value <= 0) ScoreDisplay.None else ScoreDisplay.Number(value.toString(), max)
