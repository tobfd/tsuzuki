package com.tobfd.tsuzuki.core.data.home

import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.ActivityFeedQuery
import com.tobfd.tsuzuki.core.network.HomeQuery
import com.tobfd.tsuzuki.core.network.ToggleActivityLikeMutation
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun mediaJson(id: Int, title: String) = """
    {"__typename":"Media","id":$id,"type":"ANIME","format":"TV","status":"RELEASING","episodes":12,"chapters":null,
     "volumes":null,"isAdult":false,"averageScore":80,"seasonYear":2026,
     "startDate":{"__typename":"FuzzyDate","year":2026,"month":7,"day":1},
     "title":{"__typename":"MediaTitle","userPreferred":"$title","romaji":"$title","english":null,"native":null},
     "coverImage":{"__typename":"MediaCoverImage","large":"https://img/$id.jpg","medium":null,"color":null},
     "nextAiringEpisode":null}
""".trimIndent()

private const val USER_JSON =
    """{"__typename":"User","id":5,"name":"tobfd",""" + """"avatar":{"__typename":"UserAvatar","medium":null}}"""

private val LIST_ACTIVITY_JSON = """
    {"__typename":"ListActivity","id":100,"status":"watched episode","progress":"18","createdAt":1700000000,
     "likeCount":3,"replyCount":1,"isLiked":false,"siteUrl":"https://anilist.co/activity/100",
     "user":$USER_JSON,"media":${mediaJson(154587, "Sousou no Frieren")}}
""".trimIndent()

private val TEXT_ACTIVITY_JSON = """
    {"__typename":"TextActivity","id":101,"text":"Hello <b>there</b>","createdAt":1700000100,"likeCount":0,
     "replyCount":0,"isLiked":true,"siteUrl":null,"user":$USER_JSON}
""".trimIndent()

private fun homeJson(hasNextPage: Boolean) = """
    {"data":{
      "trending":{"__typename":"Page","media":[${mediaJson(1, "Trending One")}]},
      "feed":{"__typename":"Page","pageInfo":{"__typename":"PageInfo","hasNextPage":$hasNextPage},
              "activities":[$LIST_ACTIVITY_JSON,$TEXT_ACTIVITY_JSON]}
    }}
""".trimIndent()

class DefaultHomeRepositoryTest {

    private val apollo = TestApollo()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val repository = DefaultHomeRepository(apollo.client, session)

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun firstPage_isOneRequestForTheFeedAndTrending() = runTest {
        apollo.enqueueJson(HomeQuery(), homeJson(hasNextPage = true))

        val page = repository.feedPage(FeedScope.Following, page = 1, cacheOnly = false).getOrThrow()

        assertEquals(1, apollo.requests)
        assertTrue(page.hasNextPage)
        assertFalse(page.fromCache)
        val list = page.activities[0] as Activity.ListUpdate
        assertEquals("watched episode", list.status)
        assertEquals("18", list.progress)
        assertEquals("Sousou no Frieren", list.media.title.userPreferred)
        assertEquals("Hello <b>there</b>", (page.activities[1] as Activity.Text).html)
        assertEquals(listOf("Trending One"), repository.trending.value.map { it.title.userPreferred })
        val sent = apollo.operations.single() as HomeQuery
        assertEquals(Optional.present(true), sent.isFollowing)
        assertEquals(Optional.Absent, sent.hasRepliesOrTypeText)
    }

    @Test
    fun firstPageFromCache_withoutCache_makesNoRequest() = runTest {
        val result = repository.feedPage(FeedScope.Following, page = 1, cacheOnly = true)
        assertEquals(AppError.NotFound, result.exceptionOrNull())
        assertEquals(0, apollo.requests)
    }

    @Test
    fun firstPageFromCache_afterALoad_comesFromTheCache() = runTest {
        apollo.enqueueJson(HomeQuery(), homeJson(hasNextPage = false))
        repository.feedPage(FeedScope.Following, page = 1, cacheOnly = false)

        val cached = repository.feedPage(FeedScope.Following, page = 1, cacheOnly = true).getOrThrow()

        assertTrue(cached.fromCache)
        assertEquals(2, cached.activities.size)
        assertEquals(1, apollo.requests)
    }

    @Test
    fun globalFeed_leavesOutListUpdatesWithoutRepliesLikeTheWebsite() = runTest {
        val pageInfo = """{"__typename":"PageInfo","hasNextPage":false}"""
        apollo.enqueueJson(
            ActivityFeedQuery(page = 2),
            """{"data":{"Page":{"__typename":"Page","pageInfo":$pageInfo,"activities":[$TEXT_ACTIVITY_JSON]}}}"""
        )

        val page = repository.feedPage(FeedScope.Global, page = 2, cacheOnly = false).getOrThrow()

        assertEquals(listOf(101), page.activities.map { it.id })
        val sent = apollo.operations.single() as ActivityFeedQuery
        assertEquals(2, sent.page)
        assertEquals(Optional.present(false), sent.isFollowing)
        assertEquals(Optional.present(true), sent.hasRepliesOrTypeText)
    }

    @Test
    fun offline_isAnError() = runTest {
        apollo.enqueueOffline(ActivityFeedQuery(page = 2))
        assertEquals(
            AppError.Offline,
            repository.feedPage(FeedScope.Following, page = 2, cacheOnly = false).exceptionOrNull()
        )
    }

    @Test
    fun toggleLike_returnsTheNewState() = runTest {
        apollo.enqueueJson(
            ToggleActivityLikeMutation(0),
            """{"data":{"ToggleLikeV2":{"__typename":"TextActivity","id":101,"likeCount":1,"isLiked":true}}}"""
        )
        assertEquals(true, repository.toggleLike(101).getOrThrow())
    }
}
