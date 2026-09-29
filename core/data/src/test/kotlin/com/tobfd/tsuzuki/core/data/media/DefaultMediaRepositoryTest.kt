package com.tobfd.tsuzuki.core.data.media

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.RankingType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.MediaDetailQuery
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MEDIA_CARD = """"__typename":"Media","id":154587,"type":"ANIME","format":"TV","status":"FINISHED",
 "episodes":28,"chapters":null,"volumes":null,"isAdult":false,"averageScore":91,"seasonYear":2023,
 "startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
 "title":{"__typename":"MediaTitle","userPreferred":"Sousou no Frieren","romaji":"Sousou no Frieren","english":"Frieren","native":"葬送のフリーレン"},
 "coverImage":{"__typename":"MediaCoverImage","large":"https://img/l.jpg","medium":null,"color":"#e4a15d","extraLarge":"https://img/xl.jpg"},
 "nextAiringEpisode":null"""

private val DETAIL_JSON = """
{"data":{"Media":{$MEDIA_CARD,
 "idMal":52991,"duration":24,"season":"FALL","source":"MANGA","countryOfOrigin":"JP","meanScore":91,
 "popularity":500000,"favourites":60000,"isFavourite":true,"hashtag":null,"siteUrl":"https://anilist.co/anime/154587",
 "bannerImage":"https://img/banner.jpg","endDate":{"__typename":"FuzzyDate","year":2024,"month":3,"day":22},
 "synonyms":[],"description":"An elf <i>mage</i>.","genres":["Adventure","Drama"],
 "tags":[{"__typename":"MediaTag","id":1,"name":"Elf","rank":95,"isMediaSpoiler":false,"isGeneralSpoiler":false,"description":null},
         {"__typename":"MediaTag","id":2,"name":"Twist","rank":60,"isMediaSpoiler":true,"isGeneralSpoiler":false,"description":null}],
 "rankings":[{"__typename":"MediaRank","id":1,"rank":1,"type":"RATED","allTime":true,"context":"highest rated all time","season":null,"year":null,"format":"TV"}],
 "studios":{"__typename":"StudioConnection","nodes":[{"__typename":"Studio","id":11,"name":"Madhouse"}]},
 "externalLinks":[{"__typename":"MediaExternalLink","id":1,"site":"Crunchyroll","url":"https://cr.example/frieren","type":"STREAMING","language":null,"color":null,"icon":null},
                  {"__typename":"MediaExternalLink","id":2,"site":"Twitter","url":"https://x.example","type":"SOCIAL","language":null,"color":null,"icon":null}],
 "trailer":{"__typename":"MediaTrailer","id":"abc","site":"youtube","thumbnail":null},
 "relations":{"__typename":"MediaConnection","edges":[]},
 "characters":{"__typename":"CharacterConnection","edges":[{"__typename":"CharacterEdge","role":"MAIN",
   "node":{"__typename":"Character","id":176754,"name":{"__typename":"CharacterName","userPreferred":"Frieren"},"image":{"__typename":"CharacterImage","medium":null}},
   "voiceActors":[{"__typename":"Staff","id":112215,"name":{"__typename":"StaffName","userPreferred":"Atsumi Tanezaki"},"image":{"__typename":"StaffImage","medium":null}}]}]},
 "staff":{"__typename":"StaffConnection","edges":[]},
 "stats":{"__typename":"MediaStats","statusDistribution":[{"__typename":"StatusDistribution","status":"CURRENT","amount":120}],
          "scoreDistribution":[{"__typename":"ScoreDistribution","score":100,"amount":900},{"__typename":"ScoreDistribution","score":90,"amount":500}]},
 "recommendations":{"__typename":"RecommendationConnection","nodes":[]},
 "mediaListEntry":null},
 "following":{"__typename":"Page","mediaList":[{"__typename":"MediaList","id":5,"status":"COMPLETED","score":9.5,"progress":28,
   "user":{"__typename":"User","id":9,"name":"friend","avatar":{"__typename":"UserAvatar","medium":null}}}]}
}}
""".trimIndent()

class DefaultMediaRepositoryTest {

    private val apollo = TestApollo()
    private val clock = MutableClock(Instant.parse("2026-09-29T10:00:00Z"))
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val repository = DefaultMediaRepository(apollo.client, session, clock)
    private val query = MediaDetailQuery(id = 154587, loggedIn = true)

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun detail_isMappedFromOneRequest() = runTest {
        apollo.enqueueJson(query, DETAIL_JSON)

        val detail = repository.observeDetail(154587).toList().last().getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals("Sousou no Frieren", detail.media.title.userPreferred)
        assertEquals("https://img/banner.jpg", detail.bannerUrl)
        assertEquals("https://img/xl.jpg", detail.coverUrl)
        assertEquals(listOf("Adventure", "Drama"), detail.genres)
        assertEquals(listOf(false, true), detail.tags.map { it.isSpoiler })
        assertEquals(RankingType.Rated, detail.rankings.single().type)
        assertEquals(listOf("Crunchyroll"), detail.streamingLinks.map { it.site })
        assertEquals("https://www.youtube.com/watch?v=abc", detail.trailer?.url)
        assertEquals("Atsumi Tanezaki", detail.characters.single().voiceActor?.name)
        assertEquals(mapOf(MediaListStatus.CURRENT to 120), detail.statusDistribution)
        assertEquals(listOf(90 to 500, 100 to 900), detail.scoreDistribution)
        assertEquals(9.5, detail.following.single().score, 0.0)
        assertEquals(listOf("Madhouse"), detail.info.studios)
        assertTrue(detail.isFavourite)
    }

    @Test
    fun reopeningWithinAnHour_makesNoRequest() = runTest {
        apollo.enqueueJson(query, DETAIL_JSON)
        repository.observeDetail(154587).toList()
        clock.advanceBy(Duration.ofMinutes(59))

        val again = repository.observeDetail(154587).toList()

        assertEquals(1, apollo.requests)
        assertTrue(again.single().isSuccess)
    }

    @Test
    fun offlineWithoutCache_isAnError() = runTest {
        apollo.enqueueOffline(query)
        assertEquals(AppError.Offline, repository.observeDetail(154587).toList().single().exceptionOrNull())
    }
}
