package com.tobfd.tsuzuki.core.model

import java.time.LocalDate

/** The result of a +1: what to change, and whether it finishes the entry. */
data class PlusOneResult(val changes: EntryChanges, val completes: Boolean)

/** The quick actions on a list row, as pure rules (docs/ROADMAP.md, M4). */
object ListEntryActions {

    /** +1 is offered while watching or rewatching (reading or rereading for manga). */
    fun canPlusOne(entry: MediaListEntry): Boolean {
        if (entry.status != MediaListStatus.CURRENT && entry.status != MediaListStatus.REPEATING) return false
        val total = entry.media.total
        return total == null || entry.progress < total
    }

    /**
     * One more episode or chapter. Reaching the total completes the entry: status COMPLETED and
     * finish date [today]. Finishing a rewatch also counts it (`repeat` + 1) and keeps an existing
     * finish date. Null when +1 isn't possible ([canPlusOne]).
     */
    fun plusOne(entry: MediaListEntry, today: LocalDate): PlusOneResult? {
        if (!canPlusOne(entry)) return null
        val progress = entry.progress + 1
        val total = entry.media.total
        if (total == null || progress < total) {
            return PlusOneResult(EntryChanges(progress = progress), completes = false)
        }
        val rewatch = entry.status == MediaListStatus.REPEATING
        val completedAt = if (rewatch && entry.completedAt != null) null else DateChange(FuzzyDate.of(today))
        return PlusOneResult(
            EntryChanges(
                status = MediaListStatus.COMPLETED,
                progress = progress,
                repeat = if (rewatch) entry.repeat + 1 else null,
                completedAt = completedAt
            ),
            completes = true
        )
    }

    /** Start a planned entry: watching (reading), progress 0, start date [today]. */
    fun start(today: LocalDate): EntryChanges = EntryChanges(
        status = MediaListStatus.CURRENT,
        progress = 0,
        startedAt = DateChange(FuzzyDate.of(today))
    )
}
