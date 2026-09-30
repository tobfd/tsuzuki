package com.tobfd.tsuzuki.core.data.widget

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.toModel
import com.tobfd.tsuzuki.core.database.dao.MediaListDao
import com.tobfd.tsuzuki.core.database.entity.AiringUpdate
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import com.tobfd.tsuzuki.core.network.NextEpisodesQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The next episodes of the anime the viewer is watching (the "Next episode" widget). They are read
 * from Room, where the list sync already stores each media's next episode; [refresh] updates just
 * those fields with one small request.
 */
interface AiringRepository {
    /** Anime being watched or rewatched with a scheduled next episode, soonest first. */
    val upcoming: Flow<List<UpcomingEpisode>>

    /**
     * One `NextEpisodes` request for up to [MAX_AIRING_IDS] airing anime on the list. Returns when the
     * soonest next episode airs, or null when none is scheduled (then nothing is sent either).
     */
    suspend fun refresh(): Result<Instant?>
}

/** The `perPage` of `NextEpisodes`: more airing anime than this are refreshed by the list sync only. */
const val MAX_AIRING_IDS = 50

internal class DefaultAiringRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val listDao: MediaListDao,
    private val sessionStore: SessionStore
) : AiringRepository {

    override val upcoming: Flow<List<UpcomingEpisode>> = listDao.observeEntries(MediaType.ANIME.name).map { rows ->
        rows.mapNotNull { it.toModel() }
            .filter { it.status == MediaListStatus.CURRENT || it.status == MediaListStatus.REPEATING }
            .mapNotNull { entry ->
                val episode = entry.media.nextAiringEpisode ?: return@mapNotNull null
                val airingAt = entry.media.nextAiringAt ?: return@mapNotNull null
                UpcomingEpisode(entry, episode, airingAt)
            }
            .sortedBy { it.airingAt }
    }

    override suspend fun refresh(): Result<Instant?> {
        if (sessionStore.session.first().viewer == null) return Result.failure(AppError.Unauthorized)
        val ids = listDao.airingAnimeIds(MAX_AIRING_IDS)
        if (ids.isEmpty()) return Result.success(null)
        val response = try {
            // Room holds the lists; keep this out of the Apollo cache like the list sync.
            apolloClient.query(NextEpisodesQuery(ids = Optional.present(ids)))
                .fetchPolicy(FetchPolicy.NetworkOnly)
                .doNotStore(true)
                .execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        val media = response.data?.Page?.media?.filterNotNull()
            ?: return Result.failure(AppError.Unknown("AniList returned no media"))
        val updates = media.filter { it.id in ids }.map {
            AiringUpdate(
                mediaId = it.id,
                episode = it.nextAiringEpisode?.episode,
                airingAt = it.nextAiringEpisode?.airingAt?.toLong(),
                status = it.status?.rawValue,
                episodes = it.episodes
            )
        }
        listDao.updateAiring(updates)
        return Result.success(updates.mapNotNull { it.airingAt }.minOrNull()?.let(Instant::ofEpochSecond))
    }
}
