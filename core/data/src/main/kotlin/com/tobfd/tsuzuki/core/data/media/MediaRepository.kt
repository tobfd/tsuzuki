package com.tobfd.tsuzuki.core.data.media

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.isFromCache
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.enumNamed
import com.tobfd.tsuzuki.core.data.mapper.toModel
import com.tobfd.tsuzuki.core.data.mapper.toPerson
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.CharacterRole
import com.tobfd.tsuzuki.core.model.FollowingEntry
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaDetail
import com.tobfd.tsuzuki.core.model.MediaInfo
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaRanking
import com.tobfd.tsuzuki.core.model.MediaRelation
import com.tobfd.tsuzuki.core.model.MediaSeason
import com.tobfd.tsuzuki.core.model.MediaTag
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.PersonLite
import com.tobfd.tsuzuki.core.model.RankingType
import com.tobfd.tsuzuki.core.model.Recommendation
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.StaffRole
import com.tobfd.tsuzuki.core.model.StreamingLink
import com.tobfd.tsuzuki.core.model.Trailer
import com.tobfd.tsuzuki.core.network.MediaDetailQuery
import com.tobfd.tsuzuki.core.network.ToggleFavouriteMutation
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.type.ExternalLinkType
import com.tobfd.tsuzuki.core.network.type.MediaRankType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** How long a detail page counts as fresh: reopening it within this time makes no request. */
private val DETAIL_MAX_AGE: Duration = Duration.ofHours(1)

/** Media detail pages and the favourite heart. */
interface MediaRepository {
    /**
     * The detail page, one `MediaDetail` request per open (docs/ANILIST_API.md, Caching): a cached
     * page shows at once; it is fetched again only when older than an hour. Emits a failure only
     * when there is nothing to show.
     */
    fun observeDetail(mediaId: Int): Flow<Result<MediaDetail>>

    /** Toggles the heart on a media page. */
    suspend fun toggleFavourite(mediaId: Int, type: MediaType): Result<Unit>
}

@Singleton
internal class DefaultMediaRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val sessionRepository: SessionRepository,
    private val clock: Clock
) : MediaRepository {

    private val fetchedAt = ConcurrentHashMap<Int, Instant>()

    override fun observeDetail(mediaId: Int): Flow<Result<MediaDetail>> = flow {
        val loggedIn = sessionRepository.session.first() is SessionState.LoggedIn
        val query = MediaDetailQuery(id = mediaId, loggedIn = loggedIn)
        val last = fetchedAt[mediaId]
        val fresh = last != null && Duration.between(last, clock.instant()) < DETAIL_MAX_AGE
        var shown = false
        var lastError: AppError? = null
        apolloClient.query(query)
            .fetchPolicy(if (fresh) FetchPolicy.CacheFirst else FetchPolicy.CacheAndNetwork)
            .toFlow()
            .catch { e -> lastError = (e as? ApolloException)?.toAppError() ?: AppError.Unknown(e.message) }
            .collect { response ->
                val data = response.data
                if (data != null) {
                    if (!response.isFromCache) fetchedAt[mediaId] = clock.instant()
                    data.toModel()?.let {
                        shown = true
                        emit(Result.success(it))
                    }
                } else if (!response.isFromCache) {
                    lastError = response.appErrorOrNull() ?: AppError.NotFound
                }
            }
        if (!shown) emit(Result.failure(lastError ?: AppError.NotFound))
    }

    override suspend fun toggleFavourite(mediaId: Int, type: MediaType): Result<Unit> {
        val mutation = if (type == MediaType.ANIME) {
            ToggleFavouriteMutation(animeId = Optional.present(mediaId))
        } else {
            ToggleFavouriteMutation(mangaId = Optional.present(mediaId))
        }
        val response = try {
            apolloClient.mutation(mutation).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        // The cached page still has the old heart; the next open asks AniList again.
        fetchedAt.remove(mediaId)
        return Result.success(Unit)
    }
}

internal fun MediaDetailQuery.Data.toModel(): MediaDetail? {
    val media = Media ?: return null
    val lite = media.mediaCard.toModel() ?: return null
    return MediaDetail(
        media = lite,
        bannerUrl = media.bannerImage,
        coverUrl = media.coverImage?.extraLarge ?: media.coverImage?.large ?: lite.coverUrl,
        descriptionHtml = media.description?.takeIf { it.isNotBlank() },
        genres = media.genres.orEmpty().filterNotNull(),
        tags = media.tags.orEmpty().filterNotNull().map {
            MediaTag(it.name, it.rank, isSpoiler = it.isMediaSpoiler == true || it.isGeneralSpoiler == true)
        },
        info = MediaInfo(
            episodeDuration = media.duration,
            season = enumNamed<MediaSeason>(media.season?.rawValue),
            source = media.source?.rawValue,
            startDate = media.mediaCard.startDate?.let { FuzzyDate.orNull(it.year, it.month, it.day) },
            endDate = media.endDate?.let { FuzzyDate.orNull(it.year, it.month, it.day) },
            studios = media.studios?.nodes.orEmpty().mapNotNull { it?.name },
            popularity = media.popularity,
            favourites = media.favourites,
            meanScore = media.meanScore
        ),
        isFavourite = media.isFavourite,
        siteUrl = media.siteUrl,
        rankings = media.rankings.orEmpty().filterNotNull().map {
            MediaRanking(
                rank = it.rank,
                type = if (it.type == MediaRankType.POPULAR) RankingType.Popular else RankingType.Rated,
                allTime = it.allTime == true,
                year = it.year,
                season = it.season?.rawValue
            )
        },
        streamingLinks = media.externalLinks.orEmpty().filterNotNull()
            .filter { it.type == ExternalLinkType.STREAMING && it.url != null }
            .map { StreamingLink(it.site, it.url!!, it.color) },
        trailer = media.trailer?.let { trailer ->
            val site = trailer.site ?: return@let null
            val id = trailer.id ?: return@let null
            Trailer(site, id)
        },
        relations = media.relations?.edges.orEmpty().mapNotNull { edge ->
            val related = edge?.node?.mediaCard?.toModel() ?: return@mapNotNull null
            MediaRelation(edge.relationType?.rawValue ?: "OTHER", related)
        },
        characters = media.characters?.edges.orEmpty().mapNotNull { edge ->
            val character = edge?.node?.characterLite?.toPerson() ?: return@mapNotNull null
            CharacterRole(character, edge.role?.rawValue, edge.voiceActors?.firstOrNull()?.staffLite?.toPerson())
        },
        staff = media.staff?.edges.orEmpty().mapNotNull { edge ->
            val person = edge?.node?.staffLite?.toPerson() ?: return@mapNotNull null
            StaffRole(person, edge.role)
        },
        statusDistribution = media.stats?.statusDistribution.orEmpty().mapNotNull { item ->
            val status = enumNamed<MediaListStatus>(item?.status?.rawValue) ?: return@mapNotNull null
            status to (item?.amount ?: 0)
        }.toMap(),
        scoreDistribution = media.stats?.scoreDistribution.orEmpty().mapNotNull { item ->
            val score = item?.score ?: return@mapNotNull null
            score to (item.amount ?: 0)
        }.sortedBy { it.first },
        recommendations = media.recommendations?.nodes.orEmpty().mapNotNull { node ->
            val recommended = node?.mediaRecommendation?.mediaCard?.toModel() ?: return@mapNotNull null
            Recommendation(node.rating ?: 0, recommended)
        },
        following = following?.mediaList.orEmpty().mapNotNull { item ->
            val user = item?.user?.userLite?.toModel() ?: return@mapNotNull null
            val status = enumNamed<MediaListStatus>(item.status?.rawValue) ?: return@mapNotNull null
            FollowingEntry(user, status, item.score ?: 0.0, item.progress ?: 0)
        }
    )
}
