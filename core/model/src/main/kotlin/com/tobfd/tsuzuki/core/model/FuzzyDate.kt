package com.tobfd.tsuzuki.core.model

import java.time.LocalDate

/** AniList's `FuzzyDate`: any part may be unknown, e.g. only a year. */
data class FuzzyDate(val year: Int?, val month: Int?, val day: Int?) {

    /** The full date, or null when a part is missing or the parts are not a valid date. */
    fun toLocalDateOrNull(): LocalDate? {
        val year = year ?: return null
        val month = month ?: return null
        val day = day ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    companion object {
        fun of(date: LocalDate): FuzzyDate = FuzzyDate(date.year, date.monthValue, date.dayOfMonth)

        /** Null when all parts are missing, which is how AniList says "no date". */
        fun orNull(year: Int?, month: Int?, day: Int?): FuzzyDate? =
            if (year == null && month == null && day == null) null else FuzzyDate(year, month, day)
    }
}
