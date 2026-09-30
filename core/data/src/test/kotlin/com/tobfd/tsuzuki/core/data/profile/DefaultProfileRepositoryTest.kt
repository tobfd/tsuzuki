package com.tobfd.tsuzuki.core.data.profile

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.model.ActivityDay
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.ToggleFollowMutation
import com.tobfd.tsuzuki.core.network.UserFollowersQuery
import com.tobfd.tsuzuki.core.network.UserProfileQuery
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun mediaJson(id: Int, adult: Boolean = false) = """
    {"__typename":"Media","id":$id,"type":"ANIME","format":"TV","status":"FINISHED","episodes":28,"chapters":null,
     "volumes":null,"isAdult":$adult,"averageScore":91,"seasonYear":2023,
     "startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
     "title":{"__typename":"MediaTitle","userPreferred":"Media $id","romaji":"Media $id","english":null,"native":null},
     "coverImage":{"__typename":"MediaCoverImage","large":null,"medium":null,"color":null},
     "nextAiringEpisode":null}
""".trimIndent()

private const val USER_LITE =
    """"__typename":"User","id":5,"name":"tobfd","avatar":{"__typename":"UserAvatar","medium":null}"""

private val PROFILE_JSON = """
    {"data":{"User":{"__typename":"User","id":5,"name":"tobfd","about":"","avatar":{"__typename":"UserAvatar","large":"https://img/a.png"},
     "bannerImage":null,"siteUrl":"https://anilist.co/user/tobfd","isFollowing":false,"isFollower":true,
     "statistics":{"__typename":"UserStatisticTypes",
       "anime":{"__typename":"UserStatistics","count":120,"episodesWatched":2880,"minutesWatched":69120,"meanScore":78.5,"standardDeviation":10.2,
         "statuses":[{"__typename":"UserStatusStatistic","status":"COMPLETED","count":100},{"__typename":"UserStatusStatistic","status":"CURRENT","count":20}]},
       "manga":{"__typename":"UserStatistics","count":3,"chaptersRead":400,"volumesRead":40,"meanScore":0.0,"standardDeviation":0.0,"statuses":[]}},
     "stats":{"__typename":"UserStats","activityHistory":[
       {"__typename":"UserActivityHistory","date":1759190400,"amount":3,"level":2},
       {"__typename":"UserActivityHistory","date":1759104000,"amount":1,"level":1}]},
     "favourites":{"__typename":"Favourites",
       "anime":{"__typename":"MediaConnection","nodes":[${mediaJson(154587)},${mediaJson(999, adult = true)}]},
       "manga":{"__typename":"MediaConnection","nodes":[]},
       "characters":{"__typename":"CharacterConnection","nodes":[{"__typename":"Character","id":176754,"name":{"__typename":"CharacterName","userPreferred":"Frieren"},"image":{"__typename":"CharacterImage","medium":null}}]},
       "staff":{"__typename":"StaffConnection","nodes":[]}}},
     "activity":{"__typename":"Page","pageInfo":{"__typename":"PageInfo","hasNextPage":true},"activities":[
       {"__typename":"TextActivity","id":101,"text":"Hello","createdAt":1700000100,"likeCount":0,"replyCount":0,"isLiked":false,"siteUrl":null,"user":{$USER_LITE}}]}}}
""".trimIndent()

class DefaultProfileRepositoryTest {

    private val apollo = TestApollo()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val repository = DefaultProfileRepository(apollo.client, session)

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun profile_isOneRequestWithStatsHistoryFavouritesAndActivity() = runTest {
        apollo.enqueueJson(UserProfileQuery(id = 5), PROFILE_JSON)

        val profile = repository.profile(5).getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals("tobfd", profile.name)
        assertNull(profile.aboutHtml)
        assertTrue(profile.isFollower)
        assertFalse(profile.isFollowing)
        assertEquals(120, profile.anime.count)
        assertEquals(2880, profile.anime.progress)
        assertEquals(48.0, profile.anime.daysWatched, 0.001)
        assertEquals(mapOf(MediaListStatus.COMPLETED to 100, MediaListStatus.CURRENT to 20), profile.anime.statuses)
        assertEquals(400, profile.manga.progress)
        // Oldest first, dates in UTC.
        assertEquals(
            listOf(ActivityDay(LocalDate.of(2025, 9, 29), 1), ActivityDay(LocalDate.of(2025, 9, 30), 3)),
            profile.activityHistory
        )
        // Adult favourites stay hidden for this viewer.
        assertEquals(listOf(154587), profile.favourites.anime.map { it.id })
        assertEquals("Frieren", profile.favourites.characters.single().name)
        assertEquals(1, profile.recentActivity.size)
    }

    @Test
    fun profile_offlineWithoutCache_fails() = runTest {
        apollo.enqueueOffline(UserProfileQuery(id = 5))
        assertEquals(AppError.Offline, repository.profile(5).exceptionOrNull())
    }

    @Test
    fun followers_areTheFirstPageWithFollowsYou() = runTest {
        apollo.enqueueJson(
            UserFollowersQuery(id = 5, page = 1),
            """{"data":{"Page":{"__typename":"Page","pageInfo":{"__typename":"PageInfo","hasNextPage":false},
               "followers":[{$USER_LITE,"isFollower":true}]}}}"""
        )

        val page = repository.follows(5, followers = true).getOrThrow()

        assertEquals("tobfd", page.items.single().user.name)
        assertTrue(page.items.single().followsViewer)
        assertFalse(page.hasNextPage)
    }

    @Test
    fun toggleFollow_returnsTheNewState() = runTest {
        apollo.enqueueJson(
            ToggleFollowMutation(userId = 7),
            """{"data":{"ToggleFollow":{"__typename":"User","id":7,"isFollowing":true}}}"""
        )
        assertTrue(repository.toggleFollow(7).getOrThrow())
    }
}
