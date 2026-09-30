package com.tobfd.tsuzuki.core.model

import java.time.LocalDate

/** Mirrors AniList's `MediaSeason`: the quarter an anime started airing in. */
enum class MediaSeason {
    WINTER,
    SPRING,
    SUMMER,
    FALL;

    companion object {
        /** AniList's seasons: Winter is December to February, counted to the year of its January. */
        fun of(date: LocalDate): Pair<MediaSeason, Int> = when (date.monthValue) {
            12 -> WINTER to date.year + 1
            1, 2 -> WINTER to date.year
            3, 4, 5 -> SPRING to date.year
            6, 7, 8 -> SUMMER to date.year
            else -> FALL to date.year
        }
    }
}
