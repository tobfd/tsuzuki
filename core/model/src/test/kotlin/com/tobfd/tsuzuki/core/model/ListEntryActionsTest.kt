package com.tobfd.tsuzuki.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListEntryActionsTest {

    private val today = LocalDate.of(2026, 9, 29)

    private fun entry(
        status: MediaListStatus = MediaListStatus.CURRENT,
        progress: Int = 18,
        episodes: Int? = 28,
        repeat: Int = 0,
        completedAt: FuzzyDate? = null
    ) = MediaListEntry(
        id = 1,
        mediaId = 154587,
        status = status,
        scoreRaw = 0,
        progress = progress,
        progressVolumes = 0,
        repeat = repeat,
        isPrivate = false,
        notes = "",
        hiddenFromStatusLists = false,
        customLists = emptySet(),
        startedAt = null,
        completedAt = completedAt,
        updatedAt = null,
        media = MediaLite(
            id = 154587,
            type = MediaType.ANIME,
            format = MediaFormat.TV,
            status = MediaStatus.FINISHED,
            episodes = episodes,
            chapters = null,
            volumes = null,
            title = MediaTitle("Sousou no Frieren", "Sousou no Frieren", "Frieren: Beyond Journey's End", "葬送のフリーレン"),
            coverUrl = null,
            coverColor = null,
            year = 2023,
            averageScore = 91,
            nextAiringEpisode = null,
            isAdult = false
        )
    )

    @Test
    fun plusOne_belowTotal_onlyRaisesProgress() {
        val result = ListEntryActions.plusOne(entry(progress = 18), today)
        assertEquals(PlusOneResult(EntryChanges(progress = 19), completes = false), result)
    }

    @Test
    fun plusOne_whenReachingTotal_marksCompletedWithTodaysDate() {
        val result = ListEntryActions.plusOne(entry(progress = 27), today)!!
        assertTrue(result.completes)
        assertEquals(
            EntryChanges(
                status = MediaListStatus.COMPLETED,
                progress = 28,
                completedAt = DateChange(FuzzyDate(2026, 9, 29))
            ),
            result.changes
        )
    }

    @Test
    fun plusOne_finishingARewatch_countsItAndKeepsTheFirstFinishDate() {
        val result = ListEntryActions.plusOne(
            entry(status = MediaListStatus.REPEATING, progress = 27, repeat = 1, completedAt = FuzzyDate(2024, 3, 22)),
            today
        )!!
        assertTrue(result.completes)
        assertEquals(EntryChanges(status = MediaListStatus.COMPLETED, progress = 28, repeat = 2), result.changes)
    }

    @Test
    fun plusOne_withUnknownTotal_neverCompletes() {
        val result = ListEntryActions.plusOne(entry(progress = 500, episodes = null), today)!!
        assertFalse(result.completes)
        assertEquals(EntryChanges(progress = 501), result.changes)
    }

    @Test
    fun plusOne_isOnlyForWatchingAndRewatching() {
        assertNull(ListEntryActions.plusOne(entry(status = MediaListStatus.PLANNING), today))
        assertNull(ListEntryActions.plusOne(entry(status = MediaListStatus.PAUSED), today))
        assertNull(ListEntryActions.plusOne(entry(progress = 28), today))
        assertTrue(ListEntryActions.canPlusOne(entry(status = MediaListStatus.REPEATING)))
    }

    @Test
    fun start_setsWatchingProgressZeroAndToday() {
        assertEquals(
            EntryChanges(
                status = MediaListStatus.CURRENT,
                progress = 0,
                startedAt = DateChange(FuzzyDate(2026, 9, 29))
            ),
            ListEntryActions.start(today)
        )
    }

    @Test
    fun changes_applyAndDiffBackToTheSameFields() {
        val before = entry()
        val changes = EntryChanges(scoreRaw = 85, notes = "Rewatch soon", startedAt = DateChange(FuzzyDate(2026, 1, 5)))
        val after = changes.applyTo(before)

        assertEquals(85, after.scoreRaw)
        assertEquals(FuzzyDate(2026, 1, 5), after.startedAt)
        assertEquals(changes, EntryChanges.between(before, after))
        assertTrue(EntryChanges.between(before, before).isEmpty())
    }

    @Test
    fun changes_canRemoveADate() {
        val before = entry(completedAt = FuzzyDate(2024, 3, 22))
        val after = EntryChanges(completedAt = DateChange(null)).applyTo(before)
        assertNull(after.completedAt)
    }

    @Test
    fun then_letsTheLaterChangeWin() {
        val merged = EntryChanges(progress = 19, scoreRaw = 80).then(EntryChanges(progress = 20))
        assertEquals(EntryChanges(progress = 20, scoreRaw = 80), merged)
    }
}
