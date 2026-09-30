package com.tobfd.tsuzuki.core.data.notifications

import androidx.paging.testing.asSnapshot
import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.data.requestVariables
import com.tobfd.tsuzuki.core.model.ActivityNotificationKind
import com.tobfd.tsuzuki.core.model.ListActivitySummary
import com.tobfd.tsuzuki.core.model.MediaNotificationKind
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.NotificationFilter
import com.tobfd.tsuzuki.core.network.MarkNotificationsReadQuery
import com.tobfd.tsuzuki.core.network.NotificationsQuery
import com.tobfd.tsuzuki.core.network.UnreadNotificationCountQuery
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun user(id: Int) =
    """{"__typename":"User","id":$id,"name":"user$id","avatar":{"__typename":"UserAvatar","medium":null}}"""

private fun like(id: Int, userId: Int, activityId: Int) = """
    {"__typename":"ActivityLikeNotification","id":$id,"type":"ACTIVITY_LIKE","createdAt":${1_790_000_000 - id},
     "context":" liked your activity.","activityId":$activityId,"user":${user(userId)},
     "activity":{"__typename":"ListActivity","id":$activityId,"status":"watched episode","progress":"17 - 18",
       "media":{"__typename":"Media","id":154587,"title":{"__typename":"MediaTitle","userPreferred":"Frieren"}}}}
""".trimIndent()

private fun follow(id: Int, userId: Int) = """
    {"__typename":"FollowingNotification","id":$id,"type":"FOLLOWING","createdAt":${1_790_000_000 - id},
     "context":" started following you.","user":${user(userId)}}
""".trimIndent()

private fun airing(id: Int) = """
    {"__typename":"AiringNotification","id":$id,"type":"AIRING","createdAt":${1_790_000_000 - id},"episode":18,
     "contexts":["Episode "," of "," aired."],
     "media":{"__typename":"Media","id":154587,"type":"ANIME","format":"TV","status":"FINISHED","episodes":28,
       "chapters":null,"volumes":null,"isAdult":false,"averageScore":91,"seasonYear":2023,
       "startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
       "title":{"__typename":"MediaTitle","userPreferred":"Frieren","romaji":"Sousou no Frieren","english":null,"native":null},
       "coverImage":{"__typename":"MediaCoverImage","large":null,"medium":null,"color":"#e4a15d"},
       "nextAiringEpisode":null}}
""".trimIndent()

private fun deletion(id: Int) = """
    {"__typename":"MediaDeletionNotification","id":$id,"type":"MEDIA_DELETION","createdAt":${1_790_000_000 - id},
     "context":" was deleted from the site","reason":"Duplicate","deletedMediaTitle":"Old entry"}
""".trimIndent()

/** A forum notification: the query has no fields for it, so only the type name comes back. */
private fun forum(id: Int) = """{"__typename":"ThreadCommentMentionNotification"}"""

private fun pageJson(items: List<String>, hasNextPage: Boolean, viewerCount: Int? = null): String {
    val viewer = viewerCount?.let { """"Viewer":{"__typename":"User","id":1,"unreadNotificationCount":$it},""" } ?: ""
    return """
        {"data":{$viewer"Page":{"__typename":"Page","pageInfo":{"__typename":"PageInfo","hasNextPage":$hasNextPage},
         "notifications":[${items.joinToString(",")}]}}}
    """.trimIndent()
}

private fun firstPage() = NotificationsQuery(page = 1, reset = Optional.present(true))

private fun laterPage(page: Int) = NotificationsQuery(page = page)

class NotificationsPagingTest {

    private val apollo = TestApollo()
    private val clock = MutableClock(Instant.parse("2026-09-30T12:00:00Z"))
    private val repository = DefaultNotificationsRepository(apollo.client, clock)

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun firstPage_resetsTheCountAndMarksTheNewestAsUnread() = runTest {
        apollo.enqueueJson(
            firstPage(),
            pageJson(listOf(follow(1, 11), airing(2), follow(3, 12)), false, viewerCount = 2)
        )
        val visit = repository.startVisit()

        val entries = repository.notifications(NotificationFilter.All, visit).asSnapshot()

        assertEquals(listOf(true, true, false), entries.map { it.isUnread })
        assertEquals(0, repository.unreadCount.value)
        assertTrue(apollo.operations.single().requestVariables().contains("\"reset\":true"))
    }

    @Test
    fun allFilter_leavesTypesOut() = runTest {
        // An explicit "types":null makes AniList answer HTTP 500 (seen on the phone, 2026-10-01).
        apollo.enqueueJson(firstPage(), pageJson(listOf(follow(1, 11)), false, viewerCount = 0))

        repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        val variables = apollo.operations.single().requestVariables()
        assertFalse(variables, variables.contains("types"))
    }

    @Test
    fun unknownAndIncompleteNotifications_areSkippedWithoutFailingThePage() = runTest {
        val items = listOf(
            forum(1),
            """{"__typename":"MediaSubmissionUpdate"}""",
            // Media or user gone (deleted on AniList): nothing to show.
            """{"__typename":"AiringNotification","id":3,"type":"AIRING","createdAt":1790000000,"episode":5,
               "contexts":null,"media":null}""",
            """{"__typename":"FollowingNotification","id":4,"type":"FOLLOWING","createdAt":1790000000,
               "context":null,"user":null}""",
            """{"__typename":"ActivityLikeNotification","id":5,"type":"ACTIVITY_LIKE","createdAt":null,
               "context":null,"activityId":9,"user":${user(15)},"activity":null}""",
            follow(6, 16)
        )
        apollo.enqueueJson(firstPage(), pageJson(items, false, viewerCount = 0))

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        assertEquals(listOf(5, 6), entries.map { it.notification.id })
        assertEquals(null, (entries.first().notification as Notification.ActivityEvent).listActivity)
    }

    @Test
    fun listActivityWithoutMedia_keepsStatusAndProgress() = runTest {
        // What AniList really sends for a like on a list update (seen on the phone, 2026-10-01).
        val like = """
            {"__typename":"ActivityLikeNotification","id":1,"type":"ACTIVITY_LIKE","createdAt":1790624595,
             "context":" liked your activity.","activityId":1162048298,"user":${user(11)},
             "activity":{"__typename":"ListActivity","id":1162048298,"status":"watched episode","progress":"2 - 16",
               "media":null}}
        """.trimIndent()
        apollo.enqueueJson(firstPage(), pageJson(listOf(like), false, viewerCount = 0))

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        val event = entries.single().notification as Notification.ActivityEvent
        assertEquals(
            ListActivitySummary("watched episode", "2 - 16", mediaId = null, mediaTitle = null),
            event.listActivity
        )
    }

    @Test
    fun graphQlErrorsNextToData_stillShowThePage() = runTest {
        val json = pageJson(listOf(follow(1, 11)), false, viewerCount = 0).removeSuffix("}") +
            ""","errors":[{"message":"Media not found","status":404}]}"""
        apollo.enqueueJson(firstPage(), json)

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        assertEquals(listOf(1), entries.map { it.notification.id })
    }

    @Test
    fun badgeCountAtOpen_winsWhenAniListReportsFewer() = runTest {
        repository.markBadge(3)
        apollo.enqueueJson(firstPage(), pageJson(listOf(follow(1, 11), follow(2, 12), follow(3, 13)), false, 0))

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        assertEquals(listOf(true, true, true), entries.map { it.isUnread })
    }

    @Test
    fun hiddenTypes_areLeftOutButStillCountAsUnread() = runTest {
        apollo.enqueueJson(
            firstPage(),
            pageJson(listOf(forum(1), follow(2, 11), follow(3, 12)), false, viewerCount = 2)
        )

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        assertEquals(listOf(2, 3), entries.map { it.notification.id })
        assertEquals(listOf(true, false), entries.map { it.isUnread })
    }

    @Test
    fun consecutiveLikesOnTheSameActivity_becomeOneEntry() = runTest {
        val items =
            listOf(
                like(1, 11, 100),
                like(2, 12, 100),
                like(3, 13, 100),
                follow(4, 14),
                like(5, 15, 100),
                like(6, 16, 200)
            )
        apollo.enqueueJson(firstPage(), pageJson(items, false, viewerCount = 2))

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit()).asSnapshot()

        assertEquals(listOf(1, 4, 5, 6), entries.map { it.notification.id })
        val group = entries.first().notification as Notification.ActivityEvent
        assertEquals(listOf(11, 12, 13), group.users.map { it.id })
        assertEquals(ActivityNotificationKind.Like, group.kind)
        assertEquals(ListActivitySummary("watched episode", "17 - 18", 154587, "Frieren"), group.listActivity)
        assertTrue(entries.first().isUnread)
    }

    @Test
    fun likeRunAtThePageEnd_continuesOnTheNextPage() = runTest {
        apollo.enqueueJson(firstPage(), pageJson(listOf(follow(1, 11), like(2, 12, 100), like(3, 13, 100)), true, 0))
        apollo.enqueueJson(laterPage(2), pageJson(listOf(like(4, 14, 100), airing(5)), false))

        val entries = repository.notifications(NotificationFilter.All, repository.startVisit())
            .asSnapshot { appendScrollWhile { true } }

        assertEquals(listOf(1, 2, 5), entries.map { it.notification.id })
        assertEquals(listOf(12, 13, 14), (entries[1].notification as Notification.ActivityEvent).users.map { it.id })
        val secondRequest = apollo.operations[1].requestVariables()
        assertTrue(secondRequest, secondRequest.contains("\"reset\":false"))
    }

    @Test
    fun filterChip_asksForItsTypes() = runTest {
        apollo.enqueueJson(firstPage(), pageJson(listOf(deletion(1)), false, 0))

        val entries = repository.notifications(NotificationFilter.Media, repository.startVisit()).asSnapshot()

        val variables = apollo.operations.single().requestVariables()
        assertTrue(
            variables,
            variables.contains(
                "\"types\":[\"RELATED_MEDIA_ADDITION\",\"MEDIA_DATA_CHANGE\",\"MEDIA_MERGE\",\"MEDIA_DELETION\"]"
            )
        )
        val event = entries.single().notification as Notification.MediaEvent
        assertEquals(MediaNotificationKind.Deletion, event.kind)
        assertEquals(listOf("Old entry"), event.otherTitles)
        assertEquals("Duplicate", event.reason)
    }

    @Test
    fun failedFirstPage_sendsTheResetAgainOnRetry() = runTest {
        repository.markBadge(4)
        apollo.enqueueOffline(firstPage())
        val visit = repository.startVisit()

        // asSnapshot rethrows the load error.
        val failure = runCatching { repository.notifications(NotificationFilter.All, visit).asSnapshot() }

        assertEquals(AppError.Offline, failure.exceptionOrNull())
        assertEquals(4, repository.unreadCount.value)
        apollo.enqueueJson(firstPage(), pageJson(listOf(follow(1, 11)), false, 4))
        repository.notifications(NotificationFilter.All, visit).asSnapshot()
        val retry = apollo.operations.last().requestVariables()
        assertTrue(retry, retry.contains("\"reset\":true"))
        assertEquals(0, repository.unreadCount.value)
    }

    @Test
    fun markAllRead_resetsTheBadge() = runTest {
        repository.markBadge(5)
        apollo.enqueueJson(
            MarkNotificationsReadQuery(),
            """{"data":{"Page":{"__typename":"Page","notifications":[{"__typename":"AiringNotification"}]}}}"""
        )

        val result = repository.markAllRead()

        assertTrue(result.isSuccess)
        assertEquals(0, repository.unreadCount.value)
    }

    @Test
    fun markAllRead_offline_keepsTheBadgeAndFails() = runTest {
        repository.markBadge(5)
        apollo.enqueueOffline(MarkNotificationsReadQuery())

        val result = repository.markAllRead()

        assertEquals(AppError.Offline, result.exceptionOrNull())
        assertEquals(5, repository.unreadCount.value)
    }

    /** Sets the badge through the count query, as the app does. */
    private suspend fun DefaultNotificationsRepository.markBadge(count: Int) {
        apollo.enqueueJson(
            UnreadNotificationCountQuery(),
            """{"data":{"Viewer":{"__typename":"User","id":1,"unreadNotificationCount":$count}}}"""
        )
        refreshUnreadCount(force = true)
    }
}
