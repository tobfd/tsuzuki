package com.tobfd.tsuzuki.core.data.list

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.network.MediaListCollectionQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.type.MediaType as NetworkMediaType
import javax.inject.Inject
import javax.inject.Singleton

/** At most this many chunks of 500 entries are loaded for someone else's list. */
private const val MAX_CHUNKS = 10

/** Someone else's list, or the fact that AniList won't show it. */
sealed interface OtherUserList {
    data class Visible(val list: UserList) : OtherUserList

    /** The user keeps their lists private. */
    data object Private : OtherUserList
}

/**
 * Other users' lists, read only (docs/ROADMAP.md, M9): `MediaListCollection` by user id, kept in
 * the Apollo cache only, never in Room (Room holds the viewer's own list). One request per chunk
 * of 500 entries, only when the list is opened.
 */
interface UserListRepository {
    suspend fun list(userId: Int, type: MediaType): Result<OtherUserList>
}

@Singleton
internal class DefaultUserListRepository @Inject constructor(private val apolloClient: ApolloClient) :
    UserListRepository {

    override suspend fun list(userId: Int, type: MediaType): Result<OtherUserList> {
        val entries = mutableListOf<MediaListEntry>()
        val customLists = mutableListOf<String>()
        var chunk = 1
        while (chunk <= MAX_CHUNKS) {
            val query = MediaListCollectionQuery(
                userId = userId,
                type = NetworkMediaType.safeValueOf(type.name),
                chunk = Optional.present(chunk)
            )
            val response = try {
                apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkFirst).execute()
            } catch (e: ApolloException) {
                return Result.failure(e.toAppError())
            }
            val collection = response.data?.MediaListCollection
            if (collection == null) {
                // AniList says "Private User" (whatever the status) for lists kept private.
                val private = response.errors.orEmpty().any { it.message.contains("private", ignoreCase = true) }
                if (private) return Result.success(OtherUserList.Private)
                return Result.failure(response.appErrorOrNull() ?: AppError.NotFound)
            }
            collection.lists.orEmpty().filterNotNull().forEach { group ->
                if (group.isCustomList == true) group.name?.let { if (it !in customLists) customLists += it }
                group.entries.orEmpty().forEach { entry ->
                    val (row, media) = entry?.mediaListEntryFull?.toEntities(syncRun = 0) ?: return@forEach
                    val model = media.toModel()?.let { row.toModel(it) } ?: return@forEach
                    // An entry is in its status list and in every custom list it belongs to.
                    if (entries.none { it.id == model.id }) entries += model
                }
            }
            if (collection.hasNextChunk != true) break
            chunk++
        }
        return Result.success(OtherUserList.Visible(UserList(type, entries, customLists)))
    }
}
