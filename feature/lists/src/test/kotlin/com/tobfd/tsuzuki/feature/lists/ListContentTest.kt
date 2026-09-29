package com.tobfd.tsuzuki.feature.lists

import com.tobfd.tsuzuki.core.data.list.UserList
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.testing.SampleData.listEntry
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ListContentTest {

    private val watching =
        listEntry(1, title = "Sousou no Frieren", englishTitle = "Frieren", progress = 18, scoreRaw = 90)
    private val watchingToo =
        listEntry(2, title = "Apothecary Diaries", progress = 3, scoreRaw = 70, customLists = setOf("Favs"))
    private val planned = listEntry(3, title = "Dandadan", status = MediaListStatus.PLANNING)
    private val hidden = listEntry(4, title = "Bocchi", hiddenFromStatusLists = true, customLists = setOf("Favs"))
    private val list =
        UserList(MediaType.ANIME, listOf(watching, watchingToo, planned, hidden), customLists = listOf("Favs", "Later"))

    private fun rows(tab: ListTabKey, query: String = "", sort: ListSort = ListSort.Title) =
        rowsOf(list, tab, query, sort, Locale.ENGLISH).map { it.id }

    @Test
    fun tabs_areAllStatusesThenCustomListsWithCounts() {
        val tabs = tabsOf(list)
        assertEquals(
            listOf(
                ListTab(ListTabKey.Status(MediaListStatus.CURRENT), 2),
                ListTab(ListTabKey.Status(MediaListStatus.PLANNING), 1),
                ListTab(ListTabKey.Status(MediaListStatus.COMPLETED), 0),
                ListTab(ListTabKey.Status(MediaListStatus.PAUSED), 0),
                ListTab(ListTabKey.Status(MediaListStatus.DROPPED), 0),
                ListTab(ListTabKey.Status(MediaListStatus.REPEATING), 0),
                ListTab(ListTabKey.Custom("Favs"), 2),
                ListTab(ListTabKey.Custom("Later"), 0)
            ),
            tabs
        )
    }

    @Test
    fun statusTab_leavesOutEntriesHiddenFromStatusLists() {
        assertEquals(listOf(2, 1), rows(ListTabKey.Status(MediaListStatus.CURRENT)))
    }

    @Test
    fun customTab_showsHiddenEntriesToo() {
        assertEquals(listOf(2, 4), rows(ListTabKey.Custom("Favs")))
    }

    @Test
    fun search_looksThroughTheWholeListInEveryTitleLanguage() {
        assertEquals(listOf(1), rows(ListTabKey.Status(MediaListStatus.PLANNING), query = "frieren"))
        assertEquals(listOf(3), rows(ListTabKey.Status(MediaListStatus.CURRENT), query = " DANDA "))
    }

    @Test
    fun sortByScore_putsTheBestFirst() {
        assertEquals(listOf(1, 2), rows(ListTabKey.Status(MediaListStatus.CURRENT), sort = ListSort.Score))
    }

    @Test
    fun sortByProgress_putsTheFurthestFirst() {
        assertEquals(listOf(1, 2), rows(ListTabKey.Status(MediaListStatus.CURRENT), sort = ListSort.Progress))
    }

    @Test
    fun sortByLastUpdated_putsTheNewestFirst() {
        val older = watching.copy(updatedAt = Instant.parse("2026-01-01T00:00:00Z"))
        val newer = watchingToo.copy(updatedAt = Instant.parse("2026-09-01T00:00:00Z"))
        val never = listEntry(5, title = "Aaa", updatedAt = Instant.EPOCH).copy(updatedAt = null)
        val sorted =
            rowsOf(
                UserList(MediaType.ANIME, listOf(older, never, newer), emptyList()),
                ListTabKey.Status(MediaListStatus.CURRENT),
                "",
                ListSort.LastUpdated,
                Locale.ENGLISH
            )
        assertEquals(listOf(2, 1, 5), sorted.map { it.id })
    }

    @Test
    fun sortByStartDate_putsTheLatestFirstAndUnknownLast() {
        val early = watching.copy(startedAt = FuzzyDate(2023, 10, 1))
        val yearOnly = watchingToo.copy(startedAt = FuzzyDate(2024, null, null))
        val unknown = listEntry(5, title = "Aaa")
        val sorted =
            rowsOf(
                UserList(MediaType.ANIME, listOf(early, unknown, yearOnly), emptyList()),
                ListTabKey.Status(MediaListStatus.CURRENT),
                "",
                ListSort.StartDate,
                Locale.ENGLISH
            )
        assertEquals(listOf(2, 1, 5), sorted.map { it.id })
    }

    @Test
    fun tabKeys_surviveSavedState() {
        listOf(ListTabKey.Status(MediaListStatus.REPEATING), ListTabKey.Custom("Watch: with friends")).forEach {
            assertEquals(it, ListTabKey.decode(it.encode()))
        }
        assertEquals(null, ListTabKey.decode("S:NOPE"))
    }
}
