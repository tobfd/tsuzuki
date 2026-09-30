package com.tobfd.tsuzuki.core.data.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.data.requestVariables
import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.datastore.TokenEncryption
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.network.ActivityFeedQuery
import com.tobfd.tsuzuki.core.network.NextEpisodesQuery
import com.tobfd.tsuzuki.core.testing.InMemoryDataStore
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object PlainTokenEncryption : TokenEncryption {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(ciphertext: String): String = ciphertext
}

private val NOW = Instant.parse("2026-10-01T10:00:00Z")

private fun nextEpisodesJson(vararg media: String) =
    """{"data":{"Page":{"__typename":"Page","media":[${media.joinToString(",")}]}}}"""

private fun airingJson(id: Int, episode: Int?, airingAt: Long?, status: String = "RELEASING"): String {
    val next = if (episode == null) {
        "null"
    } else {
        """{"__typename":"AiringSchedule","episode":$episode,"airingAt":$airingAt}"""
    }
    return """{"__typename":"Media","id":$id,"status":"$status","episodes":12,"nextAiringEpisode":$next}"""
}

private const val USER_JSON =
    """{"__typename":"User","id":7,"name":"GeckoTV","avatar":{"__typename":"UserAvatar","medium":"https://img/u7.png"}}"""

private fun listActivityJson(id: Int) = """
    {"__typename":"ListActivity","id":$id,"status":"watched episode","progress":"5","createdAt":${1_700_000_000 + id},
     "likeCount":3,"replyCount":1,"isLiked":false,"siteUrl":null,"user":$USER_JSON,
     "media":{"__typename":"Media","id":154587,"type":"ANIME","format":"TV","status":"FINISHED","episodes":28,
      "chapters":null,"volumes":null,"isAdult":false,"averageScore":91,"seasonYear":2023,
      "startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
      "title":{"__typename":"MediaTitle","userPreferred":"Sousou no Frieren","romaji":"Sousou no Frieren",
       "english":null,"native":null},
      "coverImage":{"__typename":"MediaCoverImage","large":"https://img/154587.jpg","medium":null,"color":"#e4a15d"},
      "nextAiringEpisode":null}}
""".trimIndent()

private fun feedJson(vararg activities: String) = """
    {"data":{"Page":{"__typename":"Page","pageInfo":{"__typename":"PageInfo","hasNextPage":true},
     "activities":[${activities.joinToString(",")}]}}}
""".trimIndent()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetRepositoriesTest {

    private val apollo = TestApollo()
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TsuzukiDatabase::class.java
    )
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val sessionStore = SessionStore(InMemoryDataStore(emptyPreferences()), PlainTokenEncryption)
    private val airing = DefaultAiringRepository(apollo.client, database.mediaListDao(), sessionStore)
    private val friends = DefaultFriendActivityRepository(
        apollo.client,
        database.friendActivityDao(),
        sessionStore,
        Clock.fixed(NOW, ZoneOffset.UTC)
    )

    @Before
    fun setUp() = runTest {
        sessionStore.saveViewer(SampleData.viewer, NOW)
    }

    @After
    fun tearDown() {
        apollo.client.close()
        database.close()
    }

    @Test
    fun upcoming_listsWatchedAnimeWithANextEpisode_soonestFirst() = runTest {
        insert(1, mediaStatus = "RELEASING", episode = 5, airingAt = NOW.epochSecond + 7200)
        insert(2, mediaStatus = "RELEASING", episode = 9, airingAt = NOW.epochSecond + 600)
        insert(3, mediaStatus = "RELEASING", episode = 2, airingAt = NOW.epochSecond + 60, status = "PLANNING")
        insert(4, mediaStatus = "FINISHED", episode = null, airingAt = null)

        val upcoming = airing.upcoming.first()

        assertEquals(listOf(20, 10), upcoming.map { it.entry.mediaId })
        assertEquals(9, upcoming.first().episode)
        assertEquals(Instant.ofEpochSecond(NOW.epochSecond + 600), upcoming.first().airingAt)
    }

    @Test
    fun refresh_asksOnlyForAiringAnimeOnceAndStoresTheNextEpisodes() = runTest {
        insert(1, mediaStatus = "RELEASING", episode = 5, airingAt = NOW.epochSecond - 60)
        insert(2, mediaStatus = "RELEASING", episode = 11, airingAt = NOW.epochSecond + 600)
        insert(3, mediaStatus = "FINISHED", episode = null, airingAt = null)
        apollo.enqueueJson(
            NextEpisodesQuery(Optional.Absent),
            nextEpisodesJson(
                airingJson(10, episode = 6, airingAt = NOW.epochSecond + 7 * 86_400),
                airingJson(20, episode = null, airingAt = null, status = "FINISHED")
            )
        )

        val soonest = airing.refresh().getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals("""{"ids":[10,20]}""", apollo.operations.single().requestVariables())
        assertEquals(Instant.ofEpochSecond(NOW.epochSecond + 7 * 86_400), soonest)
        val upcoming = airing.upcoming.first()
        assertEquals(listOf(10), upcoming.map { it.entry.mediaId })
        assertEquals(6, upcoming.single().episode)
    }

    @Test
    fun refresh_withoutAiringAnime_sendsNothing() = runTest {
        insert(3, mediaStatus = "FINISHED", episode = null, airingAt = null)

        assertNull(airing.refresh().getOrThrow())
        assertEquals(0, apollo.requests)
    }

    @Test
    fun refresh_offline_keepsTheStoredEpisodes() = runTest {
        insert(1, mediaStatus = "RELEASING", episode = 5, airingAt = NOW.epochSecond + 60)
        apollo.enqueueOffline(NextEpisodesQuery(Optional.Absent))

        assertEquals(AppError.Offline, airing.refresh().exceptionOrNull())
        assertEquals(5, airing.upcoming.first().single().episode)
    }

    @Test
    fun loggedOut_refreshesNothing() = runTest {
        sessionStore.clear()
        insert(1, mediaStatus = "RELEASING", episode = 5, airingAt = NOW.epochSecond + 60)

        assertEquals(AppError.Unauthorized, airing.refresh().exceptionOrNull())
        assertEquals(AppError.Unauthorized, friends.refresh().exceptionOrNull())
        assertEquals(0, apollo.requests)
    }

    @Test
    fun friendActivity_keepsTheNewestTenOfOneFollowingRequest() = runTest {
        assertNull(friends.feed.first().fetchedAt)
        val query = ActivityFeedQuery(page = 1, isFollowing = Optional.present(true))
        apollo.enqueueJson(query, feedJson(*(20 downTo 1).map { listActivityJson(it) }.toTypedArray()))

        friends.refresh().getOrThrow()

        assertEquals("""{"page":1,"isFollowing":true}""", apollo.operations.single().requestVariables())
        val feed = friends.feed.first()
        assertEquals(NOW.toEpochMilli(), feed.fetchedAt?.toEpochMilli())
        assertEquals((20 downTo 11).toList(), feed.activities.map { it.id })
        val first = feed.activities.first() as Activity.ListUpdate
        assertEquals("GeckoTV", first.user.name)
        assertEquals("Sousou no Frieren", first.media.title.userPreferred)
        assertEquals("5", first.progress)
    }

    @Test
    fun friendActivity_offline_keepsTheLastPage() = runTest {
        val query = ActivityFeedQuery(page = 1, isFollowing = Optional.present(true))
        apollo.enqueueJson(query, feedJson(listActivityJson(1)))
        friends.refresh().getOrThrow()
        apollo.enqueueOffline(query)

        assertEquals(AppError.Offline, friends.refresh().exceptionOrNull())
        assertEquals(listOf(1), friends.feed.first().activities.map { it.id })
    }

    @Test
    fun friendActivity_isClearedWithTheDatabaseOnLogout() = runTest {
        val query = ActivityFeedQuery(page = 1, isFollowing = Optional.present(true))
        apollo.enqueueJson(query, feedJson(listActivityJson(1)))
        friends.refresh().getOrThrow()

        database.clearAllTables()

        assertTrue(friends.feed.first().activities.isEmpty())
    }

    private suspend fun insert(
        id: Int,
        mediaStatus: String,
        episode: Int?,
        airingAt: Long?,
        status: String = "CURRENT"
    ) {
        val dao = database.mediaListDao()
        dao.upsertMedia(
            listOf(
                MediaLiteEntity(
                    id = id * 10, type = "ANIME", format = "TV", status = mediaStatus, episodes = 12, chapters = null,
                    volumes = null, titleUserPreferred = "Media $id", titleRomaji = "Media $id", titleEnglish = null,
                    titleNative = null, coverUrl = null, coverColor = null, year = 2026, averageScore = null,
                    nextAiringEpisode = episode, isAdult = false, nextAiringAt = airingAt
                )
            )
        )
        dao.upsertEntry(
            MediaListEntryEntity(
                id = id, mediaId = id * 10, type = "ANIME", status = status, scoreRaw = 0, progress = 1,
                progressVolumes = 0, repeat = 0, isPrivate = false, notes = "", hiddenFromStatusLists = false,
                customLists = "", startedYear = null, startedMonth = null, startedDay = null, completedYear = null,
                completedMonth = null, completedDay = null, updatedAt = null, syncRun = 1
            )
        )
    }
}
