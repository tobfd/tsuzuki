package com.tobfd.tsuzuki.core.data.list

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.database.dao.MediaListDao
import com.tobfd.tsuzuki.core.database.dao.PendingMutationDao
import com.tobfd.tsuzuki.core.database.entity.PendingMutationEntity
import com.tobfd.tsuzuki.core.network.DeleteMediaListEntryMutation
import com.tobfd.tsuzuki.core.network.SaveMediaListEntryMutation
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** How a run of the queue ended. */
enum class FlushResult {
    /** Everything was sent (or rejected and rolled back). */
    Done,

    /** Offline, rate limited or AniList unavailable: try again later. */
    Retry,

    /** The token is no longer valid; the session ends, the queue with it. */
    Unauthorized
}

/** Kinds of `pending_mutation` rows. */
internal object MutationKind {
    const val SAVE = "SAVE"
    const val DELETE = "DELETE"
}

/** After this many failed attempts with an unexpected error, a change is given up and shown as rejected. */
internal const val MAX_ATTEMPTS = 5

/**
 * Sends the queued list changes to AniList, oldest first (docs/ROADMAP.md, M4). All changes of one
 * entry go out as one request: several quick +1s become one `SaveMediaListEntry`, which saves rate
 * limit. A rejected change (validation error) is rolled back in Room and kept as a message.
 */
@Singleton
class ListMutationSender @Inject internal constructor(
    private val apolloClient: ApolloClient,
    private val mutationDao: PendingMutationDao,
    private val listDao: MediaListDao
) {
    private val mutex = Mutex()

    suspend fun flush(): FlushResult = mutex.withLock { flushLocked() }

    private suspend fun flushLocked(): FlushResult {
        while (true) {
            val pending = mutationDao.pending()
            val first = pending.firstOrNull() ?: return FlushResult.Done
            val group = pending.filter { it.entryId == first.entryId }
            when (val result = send(group)) {
                is SendResult.Sent -> mutationDao.delete(group.map { it.id })

                is SendResult.Rejected -> reject(group, result.error)

                is SendResult.Failed -> {
                    mutationDao.countAttempt(group.map { it.id })
                    when {
                        result.error is AppError.Unauthorized -> return FlushResult.Unauthorized
                        result.error.isTemporary() -> return FlushResult.Retry
                        group.maxOf { it.attempts } + 1 >= MAX_ATTEMPTS -> reject(group, result.error)
                        else -> return FlushResult.Retry
                    }
                }
            }
        }
    }

    private sealed interface SendResult {
        data object Sent : SendResult

        data class Rejected(val error: AppError) : SendResult

        data class Failed(val error: AppError) : SendResult
    }

    private suspend fun send(group: List<PendingMutationEntity>): SendResult {
        val entryId = group.first().entryId
        return if (group.any { it.kind == MutationKind.DELETE }) {
            sendDelete(entryId)
        } else {
            val changes = group
                .mapNotNull { it.changes }
                .map { QueueJson.decodeFromString(ChangesJson.serializer(), it).toModel() }
                .reduce { merged, next -> merged.then(next) }
            val before = QueueJson.decodeFromString(EntrySnapshot.serializer(), group.first().previous)
            // Undone before it went out: nothing to tell AniList.
            if (changes.isNoOpFor(before)) return SendResult.Sent
            sendSave(entryId, changes.toSaveMutation(entryId), sentIds = group.map { it.id }.toSet())
        }
    }

    private suspend fun sendSave(entryId: Int, mutation: SaveMediaListEntryMutation, sentIds: Set<Long>): SendResult {
        val response = try {
            apolloClient.mutation(mutation).execute()
        } catch (e: ApolloException) {
            return SendResult.Failed(e.toAppError())
        }
        response.appErrorOrNull()?.let { return it.toSendResult(entryId) }
        val saved = response.data?.SaveMediaListEntry?.mediaListEntryFull
        // AniList's state wins, unless the user changed the entry again while this was on its way.
        val stillQueued = mutationDao.pending().any { it.entryId == entryId && it.id !in sentIds }
        if (saved != null && !stillQueued) {
            val current = listDao.getEntry(entryId)?.entry
            saved.toEntities(syncRun = current?.syncRun ?: 0)?.let { (entry, media) ->
                listDao.upsertMedia(listOf(media))
                listDao.upsertEntry(entry)
            }
        }
        return SendResult.Sent
    }

    private suspend fun sendDelete(entryId: Int): SendResult {
        val response = try {
            apolloClient.mutation(DeleteMediaListEntryMutation(entryId)).execute()
        } catch (e: ApolloException) {
            return SendResult.Failed(e.toAppError())
        }
        val error = response.appErrorOrNull() ?: return SendResult.Sent
        // Already gone on AniList: that's what we wanted.
        return if (error is AppError.NotFound) SendResult.Sent else error.toSendResult(entryId)
    }

    private suspend fun AppError.toSendResult(entryId: Int): SendResult = when (this) {
        is AppError.Validation -> SendResult.Rejected(this)

        is AppError.NotFound -> {
            // The entry was removed on another device; drop it here too.
            listDao.deleteEntry(entryId)
            SendResult.Rejected(this)
        }

        else -> SendResult.Failed(this)
    }

    /** Rolls the entry back to before the first unsent change and keeps the reason for the user. */
    private suspend fun reject(group: List<PendingMutationEntity>, error: AppError) {
        val first = group.first()
        if (error !is AppError.NotFound) {
            listDao.upsertEntry(QueueJson.decodeFromString(EntrySnapshot.serializer(), first.previous).toEntity())
        }
        val fields = (error as? AppError.Validation)?.fields
        mutationDao.markFailed(
            ids = group.map { it.id },
            reason = when (error) {
                is AppError.Validation -> RejectReason.Validation
                is AppError.NotFound -> RejectReason.NotFound
                else -> RejectReason.Other
            }.name,
            detail = when (error) {
                is AppError.Validation -> error.fields.values.flatten().firstOrNull()
                is AppError.Unknown -> error.detail
                else -> null
            },
            fields = fields?.let { QueueJson.encodeToString(FieldErrorsSerializer, it) }
        )
    }
}

internal val FieldErrorsSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

private fun AppError.isTemporary(): Boolean =
    this is AppError.Offline || this is AppError.RateLimited || this is AppError.ApiUnavailable
