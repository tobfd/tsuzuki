package com.tobfd.tsuzuki.feature.browse

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.BrowseFilter
import com.tobfd.tsuzuki.core.model.BrowseHome
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.BrowseSort
import com.tobfd.tsuzuki.core.model.MediaFormat
import com.tobfd.tsuzuki.core.model.MediaSeason
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.QuickFilter
import com.tobfd.tsuzuki.core.testing.FakeBrowseRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBrowseRepository()
    private val clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.viewModel(): BrowseViewModel = BrowseViewModel(repository, clock).also { viewModel ->
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.results.collect {} }
        runCurrent()
    }

    @Test
    fun idle_showsTheRowsOfTheSelectedType() = runTest {
        val home = BrowseHome(listOf(SampleData.frieren.media), emptyList())
        repository.homeResult = Result.success(home)
        val viewModel = viewModel()

        assertEquals(BrowseRows.Content(home), viewModel.uiState.value.rows)
        assertNull(viewModel.uiState.value.search)

        viewModel.onTypeChange(MediaType.MANGA)
        runCurrent()
        assertEquals(listOf(MediaType.ANIME, MediaType.MANGA), repository.homeRequests)
    }

    @Test
    fun idle_whenTheRowsFail_showsTheErrorAndRetries() = runTest {
        repository.homeResult = Result.failure(AppError.Offline)
        val viewModel = viewModel()
        assertEquals(BrowseRows.Error(AppError.Offline), viewModel.uiState.value.rows)

        repository.homeResult = Result.success(BrowseHome(emptyList(), emptyList()))
        viewModel.onRetryRows()
        runCurrent()
        assertTrue(viewModel.uiState.value.rows is BrowseRows.Content)
    }

    @Test
    fun typing_searchesOnlyAfterAPauseAndWithTwoCharacters() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("f")
        advanceTimeBy(1_000)
        assertNull(viewModel.uiState.value.search)

        viewModel.onQueryChange("fr")
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        assertNull(viewModel.uiState.value.search)
        advanceTimeBy(2)

        val expected = BrowseQuery(MediaType.ANIME, search = "fr", filter = BrowseFilter())
        assertEquals(expected, viewModel.uiState.value.search)
        assertEquals(listOf(expected), repository.searches)
    }

    @Test
    fun typingQuickly_sendsOnlyTheLastTerm() = runTest {
        val viewModel = viewModel()

        listOf("fr", "fri", "frie", "frieren ").forEach {
            viewModel.onQueryChange(it)
            advanceTimeBy(100)
        }
        advanceTimeBy(SEARCH_DEBOUNCE_MS)

        assertEquals(listOf("frieren"), repository.searches.map { it.search })
    }

    @Test
    fun clearingTheSearch_goesBackToTheRowsAtOnce() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("frieren")
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)

        viewModel.onQueryChange("")
        runCurrent()

        assertNull(viewModel.uiState.value.search)
    }

    @Test
    fun quickChip_setsItsFilterAndTypeAndTappingAgainClearsIt() = runTest {
        val viewModel = viewModel()

        viewModel.onQuickFilter(QuickFilter.TopManhwa)
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(MediaType.MANGA, state.type)
        assertEquals(QuickFilter.TopManhwa, state.quickFilter)
        assertEquals(BrowseFilter(countryOfOrigin = "KR", sort = BrowseSort.Score), state.filter)
        assertEquals(BrowseQuery(MediaType.MANGA, null, state.filter), repository.searches.last())

        viewModel.onQuickFilter(QuickFilter.TopManhwa)
        runCurrent()
        assertNull(viewModel.uiState.value.quickFilter)
        assertNull(viewModel.uiState.value.search)
    }

    @Test
    fun quickChip_top100_stopsAtAHundredAndThisSeasonUsesToday() = runTest {
        val viewModel = viewModel()

        viewModel.onQuickFilter(QuickFilter.Top100)
        runCurrent()
        assertEquals(100, repository.searches.last().limit)

        viewModel.onQuickFilter(QuickFilter.ThisSeason)
        runCurrent()
        val filter = repository.searches.last().filter
        assertEquals(MediaSeason.FALL, filter.season)
        assertEquals(2026, filter.year)
        assertNull(repository.searches.last().limit)
    }

    @Test
    fun switchingToManga_dropsAnimeOnlyChoicesAndChips() = runTest {
        val viewModel = viewModel()
        viewModel.onQuickFilter(QuickFilter.TopMovies)
        runCurrent()

        viewModel.onTypeChange(MediaType.MANGA)
        runCurrent()

        assertNull(viewModel.uiState.value.quickFilter)
        assertEquals(BrowseFilter(), viewModel.uiState.value.filter)
    }

    @Test
    fun filterSheet_keepsADraftUntilShowResults() = runTest {
        val viewModel = viewModel()
        viewModel.onQuickFilter(QuickFilter.Trending)
        runCurrent()

        viewModel.onOpenFilters()
        runCurrent()
        assertEquals(1, repository.optionRequests)
        val draft = viewModel.uiState.value.draft!!.copy(genres = setOf("Drama"), formats = setOf(MediaFormat.MANGA))
        viewModel.onDraftChange(draft)
        runCurrent()
        // Only anime formats stay; the applied filter doesn't move yet.
        assertEquals(emptySet<MediaFormat>(), viewModel.uiState.value.draft!!.formats)
        assertEquals(BrowseFilter(sort = BrowseSort.Trending), viewModel.uiState.value.filter)

        viewModel.onApplyFilters()
        runCurrent()
        val state = viewModel.uiState.value
        assertNull(state.draft)
        assertNull(state.quickFilter)
        assertEquals(BrowseFilter(genres = setOf("Drama"), sort = BrowseSort.Trending), state.filter)
        assertEquals(state.filter, repository.searches.last().filter)

        // Opening the sheet again doesn't ask for genres and tags again.
        viewModel.onOpenFilters()
        viewModel.onDismissFilters()
        runCurrent()
        assertEquals(1, repository.optionRequests)
        assertEquals(BrowseFilter(genres = setOf("Drama"), sort = BrowseSort.Trending), viewModel.uiState.value.filter)
    }

    @Test
    fun seeAllNewlyAdded_sortsByRecentlyAdded() = runTest {
        val viewModel = viewModel()
        viewModel.onSeeAllNewlyAdded()
        runCurrent()
        assertEquals(BrowseSort.RecentlyAdded, repository.searches.last().filter.sort)
    }
}
