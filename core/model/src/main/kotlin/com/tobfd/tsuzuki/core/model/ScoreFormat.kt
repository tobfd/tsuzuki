package com.tobfd.tsuzuki.core.model

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
