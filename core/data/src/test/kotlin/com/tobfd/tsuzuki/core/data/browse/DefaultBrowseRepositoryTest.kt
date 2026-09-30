package com.tobfd.tsuzuki.core.data.browse

import androidx.paging.testing.asSnapshot
import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.model.BrowseFilter
import com.tobfd.tsuzuki.core.model.BrowseQuery
import com.tobfd.tsuzuki.core.model.BrowseSort
import com.tobfd.tsuzuki.core.model.MediaFormat
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaSeason
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.BrowseHomeQuery
import com.tobfd.tsuzuki.core.network.GenresAndTagsQuery
import com.tobfd.tsuzuki.core.network.SearchMediaQuery
import com.tobfd.tsuzuki.core.network.type.MediaSort
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun cardJson(id: Int) = """
    {"__typename":"Media","id":$id,"type":"ANIME","format":"TV","status":"FINISHED","episodes":28,"chapters":null,
     "volumes":null,"isAdult":false,"averageScore":91,"seasonYear":2023,
     "startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
     "title":{"__typename":"MediaTitle","userPreferred":"Media $id","romaji":"Media $id","english":null,"native":null},
     "coverImage":{"__typename":"MediaCoverImage","large":null,"medium":null,"color":"#e4a15d"},
     "nextAiringEpisode":null}
""".trimIndent()

private fun mediaJson(id: Int, listStatus: String? = null) = cardJson(id).removeSuffix("}") +
    ""","mediaListEntry":""" +
    (listStatus?.let { """{"__typename":"MediaList","id":${id * 10},"status":"$it"}""" } ?: "null") + "}"

private fun searchJson(ids: List<Int>, hasNextPage: Boolean, listStatus: String? = null) = """
    {"data":{"Page":{"__typename":"Page","pageInfo":{"__typename":"PageInfo","hasNextPage":$hasNextPage},
     "media":[${ids.joinToString(",") { mediaJson(it, listStatus) }}]}}}
""".trimIndent()

private val HOME_JSON = """
    {"data":{"trending":{"__typename":"Page","media":[${cardJson(1)},${cardJson(2)}]},
             "newlyAdded":{"__typename":"Page","media":[${cardJson(3)}]}}}
""".trimIndent()

private val GENRES_JSON = """
    {"data":{"GenreCollection":["Action","Drama","Hentai"],
     "MediaTagCollection":[
       {"__typename":"MediaTag","id":1,"name":"Elf","category":"Cast-Traits","isAdult":false},
       {"__typename":"MediaTag","id":2,"name":"Nudity","category":"Sexual Content","isAdult":true},
       {"__typename":"MediaTag","id":3,"name":"anti-hero","category":"Cast-Traits","isAdult":false}]}}
""".trimIndent()

class DefaultBrowseRepositoryTest {

    private val apollo = TestApollo()
    private val clock = MutableClock(Instant.parse("2026-09-30T10:00:00Z"))
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val repository = DefaultBrowseRepository(apollo.client, session, clock)

    private val animeQuery = BrowseQuery(MediaType.ANIME, search = null, filter = BrowseFilter())

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun home_isOneRequestAndComesFromTheCacheFor30Minutes() = runTest {
        apollo.enqueueJson(BrowseHomeQuery(type = com.tobfd.tsuzuki.core.network.type.MediaType.ANIME), HOME_JSON)

        val home = repository.home(MediaType.ANIME).getOrThrow()
        repository.home(MediaType.ANIME).getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals(listOf(1, 2), home.trending.map { it.id })
        assertEquals(listOf(3), home.newlyAdded.map { it.id })

        clock.advanceBy(Duration.ofMinutes(31))
        apollo.enqueueJson(BrowseHomeQuery(type = com.tobfd.tsuzuki.core.network.type.MediaType.ANIME), HOME_JSON)
        repository.home(MediaType.ANIME).getOrThrow()
        assertEquals(2, apollo.requests)
    }

    @Test
    fun home_hidesAdultMediaUnlessTheViewerAllowsThem() = runTest {
        apollo.enqueueJson(BrowseHomeQuery(type = com.tobfd.tsuzuki.core.network.type.MediaType.ANIME), HOME_JSON)
        repository.home(MediaType.ANIME)
        assertEquals(Optional.present(false), (apollo.operations.last() as BrowseHomeQuery).isAdult)

        val adultViewer = SampleData.viewer.copy(
            options = SampleData.viewer.options.copy(displayAdultContent = true)
        )
        session.sessionState.value = SessionState.LoggedIn(adultViewer, SampleData.tokenExpiry)
        apollo.enqueueJson(BrowseHomeQuery(type = com.tobfd.tsuzuki.core.network.type.MediaType.MANGA), HOME_JSON)
        repository.home(MediaType.MANGA)
        // null, not true: true would show adult media only.
        assertEquals(Optional.present(null), (apollo.operations.last() as BrowseHomeQuery).isAdult)
    }

    @Test
    fun home_whenOfflineWithoutCache_fails() = runTest {
        apollo.enqueueOffline(BrowseHomeQuery(type = com.tobfd.tsuzuki.core.network.type.MediaType.ANIME))
        assertEquals(AppError.Offline, repository.home(MediaType.ANIME).exceptionOrNull())
    }

    @Test
    fun search_loadsOnePageUntilTheListScrolls() = runTest {
        val page = (1..SEARCH_PAGE_SIZE).toList()
        apollo.enqueueJson(SearchMediaQuery(page = 1, type = ANIME), searchJson(page, true, "CURRENT"))

        // The first screen of rows is far from the end of the page, so nothing more loads.
        val results = repository.search(animeQuery).asSnapshot { scrollTo(index = 8) }

        assertEquals(1, apollo.requests)
        assertEquals(page, results.map { it.media.id })
        assertEquals(MediaListStatus.CURRENT, results.first().listStatus)
    }

    @Test
    fun search_pagesWhileHasNextPageAndSkipsRepeatedMedia() = runTest {
        apollo.enqueueJson(SearchMediaQuery(page = 1, type = ANIME), searchJson(listOf(1, 2), true))
        apollo.enqueueJson(SearchMediaQuery(page = 2, type = ANIME), searchJson(listOf(2, 3), false))

        val results = repository.search(animeQuery).asSnapshot { appendScrollWhile { true } }

        assertEquals(2, apollo.requests)
        assertEquals(listOf(1, 2, 3), results.map { it.media.id })
        assertNull(results.first().listStatus)
        assertEquals(listOf(1, 2), apollo.operations.map { (it as SearchMediaQuery).page })
    }

    @Test
    fun search_withALimit_stopsThere() = runTest {
        apollo.enqueueJson(SearchMediaQuery(page = 1, type = ANIME), searchJson(listOf(1, 2), true))
        apollo.enqueueJson(SearchMediaQuery(page = 2, type = ANIME), searchJson(listOf(3, 4), true))

        val results = repository.search(animeQuery.copy(limit = 3)).asSnapshot { appendScrollWhile { true } }

        assertEquals(listOf(1, 2, 3), results.map { it.media.id })
        assertEquals(2, apollo.requests)
    }

    @Test
    fun searchQuery_mapsTheFilterToAniListArguments() {
        val query = BrowseQuery(
            type = MediaType.MANGA,
            search = "frieren",
            filter = BrowseFilter(
                formats = setOf(MediaFormat.MANGA),
                season = MediaSeason.FALL,
                year = 2020,
                genres = setOf("Drama"),
                countryOfOrigin = "KR"
            )
        ).toSearchMediaQuery(page = 3, isAdult = Optional.present(false))

        assertEquals(3, query.page)
        assertEquals(Optional.present("frieren"), query.search)
        assertEquals(Optional.present(listOf(MediaSort.SEARCH_MATCH)), query.sort)
        // Manga have no seasons; the year is a start date range.
        assertEquals(Optional.Absent, query.seasonYear)
        assertEquals(Optional.present(20_200_000), query.startDate_greater)
        assertEquals(Optional.present(20_210_000), query.startDate_lesser)
        assertEquals(Optional.present(listOf("Drama")), query.genre_in)
        assertEquals(Optional.Absent, query.tag_in)
        assertEquals(Optional.present("KR"), query.countryOfOrigin)
    }

    @Test
    fun searchQuery_forAnime_usesTheSeasonYearAndTheChosenSort() {
        val query = BrowseQuery(
            type = MediaType.ANIME,
            search = null,
            filter = BrowseFilter(season = MediaSeason.FALL, year = 2026, sort = BrowseSort.Trending)
        ).toSearchMediaQuery(page = 1, isAdult = Optional.present(false))

        assertEquals(Optional.Absent, query.search)
        assertEquals(Optional.present(2026), query.seasonYear)
        assertEquals(Optional.Absent, query.startDate_greater)
        assertEquals(Optional.present(listOf(MediaSort.TRENDING_DESC, MediaSort.POPULARITY_DESC)), query.sort)
    }

    @Test
    fun sort_withoutAChoice_isBestMatchWhileSearchingElsePopularity() {
        assertEquals(listOf(MediaSort.SEARCH_MATCH), sortFor(null, searching = true))
        assertEquals(listOf(MediaSort.POPULARITY_DESC), sortFor(null, searching = false))
        assertEquals(listOf(MediaSort.SCORE_DESC), sortFor(BrowseSort.Score, searching = true))
    }

    @Test
    fun filterOptions_areOneRequestPerRunAndHideAdultTags() = runTest {
        apollo.enqueueJson(GenresAndTagsQuery(), GENRES_JSON)

        val options = repository.filterOptions().getOrThrow()
        repository.filterOptions().getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals(listOf("Action", "Drama"), options.genres)
        assertEquals(listOf("anti-hero", "Elf"), options.tags.map { it.name })
    }

    private companion object {
        val ANIME = com.tobfd.tsuzuki.core.network.type.MediaType.ANIME
    }
}
