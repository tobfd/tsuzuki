package com.tobfd.tsuzuki.core.data.profile

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.enumNamed
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.data.mapper.toPerson
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.session.adultContentAllowed
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.ActivityDay
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.Favourites
import com.tobfd.tsuzuki.core.model.FollowUser
import com.tobfd.tsuzuki.core.model.ListStatistics
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.UserProfile
import com.tobfd.tsuzuki.core.network.ToggleFollowMutation
import com.tobfd.tsuzuki.core.network.UserFollowersQuery
import com.tobfd.tsuzuki.core.network.UserFollowingQuery
import com.tobfd.tsuzuki.core.network.UserProfileQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Profiles (docs/ROADMAP.md, M9): one request per profile, the Social lists on demand, follow. */
interface ProfileRepository {
    /** The whole profile in one `UserProfile` request; the cached copy when offline. */
    suspend fun profile(userId: Int): Result<UserProfile>

    /** The first page of who [userId] follows ([followers] false) or who follows them. */
    suspend fun follows(userId: Int, followers: Boolean): Result<ContentPage<FollowUser>>

    /** Follows or unfollows; returns whether the viewer follows [userId] now. */
    suspend fun toggleFollow(userId: Int): Result<Boolean>
}

@Singleton
internal class DefaultProfileRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val sessionRepository: SessionRepository
) : ProfileRepository {

    override suspend fun profile(userId: Int): Result<UserProfile> {
        val response = try {
            apolloClient.query(UserProfileQuery(id = userId)).fetchPolicy(FetchPolicy.NetworkFirst).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        val data = response.data ?: return Result.failure(response.appErrorOrNull() ?: AppError.NotFound)
        val adult = sessionRepository.adultContentAllowed()
        return data.toModel(adult)?.let { Result.success(it) } ?: Result.failure(AppError.NotFound)
    }

    override suspend fun follows(userId: Int, followers: Boolean): Result<ContentPage<FollowUser>> {
        val response = try {
            if (followers) {
                apolloClient.query(UserFollowersQuery(id = userId, page = 1)).fetchPolicy(FetchPolicy.NetworkFirst)
                    .execute()
            } else {
                apolloClient.query(UserFollowingQuery(id = userId, page = 1)).fetchPolicy(FetchPolicy.NetworkFirst)
                    .execute()
            }
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        val page = when (val data = response.data) {
            is UserFollowersQuery.Data -> data.Page?.let { page ->
                ContentPage(
                    items = page.followers.orEmpty().mapNotNull { user ->
                        user?.let { FollowUser(it.userLite.toModel(), followsViewer = it.isFollower == true) }
                    },
                    hasNextPage = page.pageInfo?.hasNextPage == true
                )
            }

            is UserFollowingQuery.Data -> data.Page?.let { page ->
                ContentPage(
                    items = page.following.orEmpty().mapNotNull { user ->
                        user?.let { FollowUser(it.userLite.toModel(), followsViewer = it.isFollower == true) }
                    },
                    hasNextPage = page.pageInfo?.hasNextPage == true
                )
            }

            else -> null
        } ?: return Result.failure(response.appErrorOrNull() ?: AppError.NotFound)
        return Result.success(page)
    }

    override suspend fun toggleFollow(userId: Int): Result<Boolean> {
        val response = try {
            apolloClient.mutation(ToggleFollowMutation(userId)).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        val following = response.data?.ToggleFollow?.isFollowing
            ?: return Result.failure(AppError.Unknown("AniList returned no follow state"))
        return Result.success(following)
    }
}

internal fun UserProfileQuery.Data.toModel(adult: Boolean): UserProfile? {
    val user = User ?: return null
    val anime = user.statistics?.anime
    val manga = user.statistics?.manga
    return UserProfile(
        id = user.id,
        name = user.name,
        aboutHtml = user.about?.takeIf { it.isNotBlank() },
        avatarUrl = user.avatar?.large,
        bannerUrl = user.bannerImage,
        siteUrl = user.siteUrl,
        isFollowing = user.isFollowing == true,
        isFollower = user.isFollower == true,
        anime = anime?.let {
            ListStatistics(
                count = it.count,
                progress = it.episodesWatched,
                volumes = 0,
                minutesWatched = it.minutesWatched,
                meanScore = it.meanScore,
                standardDeviation = it.standardDeviation,
                statuses = statuses(it.statuses.orEmpty().map { s -> s?.status?.rawValue to (s?.count ?: 0) })
            )
        } ?: ListStatistics.Empty,
        manga = manga?.let {
            ListStatistics(
                count = it.count,
                progress = it.chaptersRead,
                volumes = it.volumesRead,
                minutesWatched = 0,
                meanScore = it.meanScore,
                standardDeviation = it.standardDeviation,
                statuses = statuses(it.statuses.orEmpty().map { s -> s?.status?.rawValue to (s?.count ?: 0) })
            )
        } ?: ListStatistics.Empty,
        activityHistory = user.stats?.activityHistory.orEmpty().mapNotNull { day ->
            val date = day?.date ?: return@mapNotNull null
            ActivityDay(
                date = Instant.ofEpochSecond(date.toLong()).atZone(ZoneOffset.UTC).toLocalDate(),
                amount = day.amount ?: 0
            )
        }.sortedBy { it.date },
        favourites = Favourites(
            anime = user.favourites?.anime?.nodes.orEmpty().mapNotNull { it?.mediaCard?.toModel() }
                .filter { adult || !it.isAdult },
            manga = user.favourites?.manga?.nodes.orEmpty().mapNotNull { it?.mediaCard?.toModel() }
                .filter { adult || !it.isAdult },
            characters = user.favourites?.characters?.nodes.orEmpty().mapNotNull { it?.characterLite?.toPerson() },
            staff = user.favourites?.staff?.nodes.orEmpty().mapNotNull { it?.staffLite?.toPerson() }
        ),
        recentActivity = activity?.activities.orEmpty().mapNotNull { it?.feedActivity?.toModel() }
            .filter { adult || (it as? Activity.ListUpdate)?.media?.isAdult != true }
    )
}

private fun statuses(pairs: List<Pair<String?, Int>>): Map<MediaListStatus, Int> =
    pairs.mapNotNull { (status, count) -> enumNamed<MediaListStatus>(status)?.let { it to count } }.toMap()
