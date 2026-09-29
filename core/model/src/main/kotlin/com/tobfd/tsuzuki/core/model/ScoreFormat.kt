package com.tobfd.tsuzuki.core.model

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How a user scores media, mirroring AniList's `ScoreFormat`. AniList returns list scores already
 * converted to the viewer's format.
 */
enum class ScoreFormat {
    /** 0–100, e.g. 85. */
    POINT_100,

    /** 0–10 with one decimal, e.g. 8.5. */
    POINT_10_DECIMAL,

    /** 0–10 in whole numbers, e.g. 8. */
    POINT_10,

    /** 0–5 stars. */
    POINT_5,

    /** 1 = disliked, 2 = neutral, 3 = liked. */
    POINT_3
}

private const val POINT_3_DISLIKED_MAX = 35
private const val POINT_3_NEUTRAL_MAX = 60
private const val POINT_3_LIKED_RAW = 85
private const val RAW_PER_STAR = 20
private const val RAW_PER_POINT = 10

/**
 * A raw score (0–100, AniList's `score(format: POINT_100)`) in this format, as AniList shows it:
 * 85 is 85, 8.5, 9, 4 stars or "liked". 0 stays 0 (not scored).
 */
fun ScoreFormat.fromRaw(raw: Int): Double {
    if (raw <= 0) return 0.0
    return when (this) {
        ScoreFormat.POINT_100 -> raw.toDouble()

        ScoreFormat.POINT_10_DECIMAL -> raw / 10.0

        ScoreFormat.POINT_10 -> roundHalfUp(raw / 10.0)

        ScoreFormat.POINT_5 -> roundHalfUp(raw / 20.0)

        ScoreFormat.POINT_3 -> when {
            raw <= POINT_3_DISLIKED_MAX -> 1.0
            raw <= POINT_3_NEUTRAL_MAX -> 2.0
            else -> 3.0
        }
    }
}

/** A score in this format as a raw score (0–100), the value the list stores and sends. */
fun ScoreFormat.toRaw(score: Double): Int {
    if (score <= 0.0) return 0
    val raw = when (this) {
        ScoreFormat.POINT_100 -> score

        ScoreFormat.POINT_10_DECIMAL, ScoreFormat.POINT_10 -> score * RAW_PER_POINT

        ScoreFormat.POINT_5 -> score * RAW_PER_STAR

        ScoreFormat.POINT_3 -> when (score.roundToInt()) {
            1 -> POINT_3_DISLIKED_MAX.toDouble()
            2 -> POINT_3_NEUTRAL_MAX.toDouble()
            else -> POINT_3_LIKED_RAW.toDouble()
        }
    }
    return raw.roundToInt().coerceIn(0, 100)
}

private fun roundHalfUp(value: Double): Double = floor(value + 0.5)
