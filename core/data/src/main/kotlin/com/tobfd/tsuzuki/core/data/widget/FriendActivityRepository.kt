package com.tobfd.tsuzuki.core.data.widget

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.database.dao.FriendActivityDao
import com.tobfd.tsuzuki.core.database.entity.FriendActivityEntity
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.FriendActivityFeed
import com.tobfd.tsuzuki.core.model.MediaLite
import com.tobfd.tsuzuki.core.model.MediaTitle
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.network.ActivityFeedQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The newest activities of the people the viewer follows (the "Friends' activity" widget). Only
 * the last fetched page is kept, in Room, so the widget shows it between the periodic updates and
 * offline; logout clears it with the rest of the database.
 */
interface FriendActivityRepository {
    val feed: Flow<FriendActivityFeed>

    /** One `ActivityFeed` request (Following, page 1); keeps the newest [FRIEND_ACTIVITY_COUNT]. */
    suspend fun refresh(): Result<Unit>
}

/** How many activities are kept: more than the largest widget shows, far fewer than a feed page. */
const val FRIEND_ACTIVITY_COUNT = 10

internal class DefaultFriendActivityRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val dao: FriendActivityDao,
    private val sessionStore: SessionStore,
    private val clock: Clock
) : FriendActivityRepository {

    override val feed: Flow<FriendActivityFeed> = dao.observeAll().map { rows ->
        FriendActivityFeed(
            activities = rows.mapNotNull { it.toModel() },
            fetchedAt = rows.firstOrNull()?.fetchedAt?.let(Instant::ofEpochMilli)
        )
    }

    override suspend fun refresh(): Result<Unit> {
        if (sessionStore.session.first().viewer == null) return Result.failure(AppError.Unauthorized)
        val query =
            ActivityFeedQuery(page = 1, isFollowing = Optional.present(true), hasRepliesOrTypeText = Optional.Absent)
        val response = try {
            // Room keeps the widget's copy; the Home feed's cache stays as the app left it.
            apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkOnly).doNotStore(true).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        val page = response.data?.Page ?: return Result.failure(AppError.Unknown("AniList returned no feed"))
        val fetchedAt = clock.millis()
        val activities = page.activities.orEmpty()
            .mapNotNull { it?.feedActivity?.toModel() }
            .take(FRIEND_ACTIVITY_COUNT)
            .map { it.toEntity(fetchedAt) }
        dao.replaceAll(activities)
        return Result.success(Unit)
    }
}

internal fun Activity.toEntity(fetchedAt: Long): FriendActivityEntity = when (this) {
    is Activity.ListUpdate -> FriendActivityEntity(
        id = id,
        userId = user.id,
        userName = user.name,
        userAvatarUrl = user.avatarUrl,
        createdAt = createdAt.epochSecond,
        status = status,
        progress = progress,
        html = null,
        mediaId = media.id,
        mediaType = media.type.name,
        mediaTitle = media.title.userPreferred,
        coverUrl = media.coverUrl,
        coverColor = media.coverColor,
        fetchedAt = fetchedAt
    )

    is Activity.Text -> FriendActivityEntity(
        id = id,
        userId = user.id,
        userName = user.name,
        userAvatarUrl = user.avatarUrl,
        createdAt = createdAt.epochSecond,
        status = null,
        progress = null,
        html = html,
        mediaId = null,
        mediaType = null,
        mediaTitle = null,
        coverUrl = null,
        coverColor = null,
        fetchedAt = fetchedAt
    )
}

/**
 * Back to the feed model. The widget keeps only what it shows, so a list update's media has just its
 * id, type, title and cover; likes and replies are not kept.
 */
internal fun FriendActivityEntity.toModel(): Activity? {
    val user = UserLite(userId, userName, userAvatarUrl)
    val created = Instant.ofEpochSecond(createdAt)
    html?.let { return Activity.Text(id, user, created, 0, false, 0, null, it) }
    val status = status ?: return null
    val media = MediaLite(
        id = mediaId ?: return null,
        type = MediaType.entries.firstOrNull { it.name == mediaType } ?: return null,
        format = null,
        status = null,
        episodes = null,
        chapters = null,
        volumes = null,
        title = MediaTitle(mediaTitle.orEmpty(), null, null, null),
        coverUrl = coverUrl,
        coverColor = coverColor,
        year = null,
        averageScore = null,
        nextAiringEpisode = null,
        isAdult = false
    )
    return Activity.ListUpdate(id, user, created, 0, false, 0, null, status, progress, media)
}
