package com.tobfd.tsuzuki.core.data.list

import androidx.room3.withWriteTransaction
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import com.tobfd.tsuzuki.core.database.dao.MediaListDao
import com.tobfd.tsuzuki.core.database.dao.PendingMutationDao
import com.tobfd.tsuzuki.core.database.entity.CustomListEntity
import com.tobfd.tsuzuki.core.database.entity.ListSyncEntity
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.database.entity.PendingMutationEntity
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.ListEntryActions
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.MediaListCollectionQuery
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.type.MediaType as NetworkMediaType
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val SYNC_MAX_AGE: Duration = Duration.ofMinutes(15)
private const val PER_CHUNK = 500

@Singleton
internal class DefaultListRepository @Inject constructor(
    private val apolloClient: ApolloClient,
    private val database: TsuzukiDatabase,
    private val listDao: MediaListDao,
    private val mutationDao: PendingMutationDao,
    private val sender: ListMutationSender,
    private val scheduler: ListWorkScheduler,
    private val sessionRepository: SessionRepository,
    private val clock: Clock
) : ListRepository {

    private val syncMutex = Mutex()

    override fun observeList(type: MediaType): Flow<UserList> = combine(
        listDao.observeEntries(type.name),
        listDao.observeCustomLists(type.name)
    ) { entries, customLists ->
        UserList(type, entries.mapNotNull { it.toModel() }, customLists.map { it.name })
    }

    override fun observeEntry(mediaId: Int): Flow<MediaListEntry?> =
        listDao.observeEntryByMediaId(mediaId).map { it?.toModel() }

    override val rejectedChanges: Flow<List<RejectedChange>> = mutationDao.observeFailed().map { rows ->
        rows.groupBy { it.entryId }.values.map { it.last().toRejectedChange() }
    }

    override suspend fun dismissRejection(entryId: Int) = mutationDao.dismissFailures(entryId)

    override val queuedChangeCount: Flow<Int> = mutationDao.observePendingCount()

    override suspend fun refresh(force: Boolean): Result<Unit> = syncMutex.withLock {
        val viewerId = (sessionRepository.session.first() as? SessionState.LoggedIn)?.viewer?.id
            ?: return Result.success(Unit)
        // Send first, so the sync sees our own changes; entries still queued keep their local state.
        sender.flush()
        for (type in MediaType.entries) {
            if (!force && isFresh(type, viewerId)) continue
            syncType(viewerId, type).onFailure { return Result.failure(it) }
        }
        Result.success(Unit)
    }

    private suspend fun isFresh(type: MediaType, viewerId: Int): Boolean {
        val sync = listDao.getSync(type.name) ?: return false
        return sync.userId == viewerId && clock.millis() - sync.syncedAt < SYNC_MAX_AGE.toMillis()
    }

    /** `MediaListCollection` chunk by chunk (one request each), then one transaction in Room. */
    private suspend fun syncType(viewerId: Int, type: MediaType): Result<Unit> {
        val run = clock.millis()
        val rows = LinkedHashMap<Int, Pair<MediaListEntryEntity, MediaLiteEntity>>()
        var customListNames: List<String>? = null
        val customListsFromGroups = mutableListOf<String>()
        var chunk = 1
        do {
            val query = MediaListCollectionQuery(
                userId = viewerId,
                type = NetworkMediaType.safeValueOf(type.name),
                chunk = Optional.present(chunk),
                perChunk = Optional.present(PER_CHUNK)
            )
            val response = try {
                // Room is the only store for the lists; keep them out of the Apollo cache.
                apolloClient.query(query).fetchPolicy(FetchPolicy.NetworkOnly).doNotStore(true).execute()
            } catch (e: ApolloException) {
                return Result.failure(e.toAppError())
            }
            response.appErrorOrNull()?.let { return Result.failure(it) }
            val collection = response.data?.MediaListCollection
                ?: return Result.failure(AppError.Unknown("AniList returned no list"))
            collection.lists.orEmpty().filterNotNull().forEach { group ->
                if (group.isCustomList == true) group.name?.let(customListsFromGroups::add)
                group.entries.orEmpty().filterNotNull().forEach { item ->
                    val full = item.mediaListEntryFull
                    if (customListNames == null) {
                        // Every entry lists all custom lists of the type, in the viewer's order.
                        customListNames = customListsOf(full.mediaListEntryCore.customLists).keys.toList()
                            .takeIf { it.isNotEmpty() }
                    }
                    // An entry appears in its status list and in each custom list: keep it once.
                    full.toEntities(run)?.let { rows[it.first.id] = it }
                }
            }
            chunk++
        } while (collection.hasNextChunk == true)

        val names = customListNames ?: customListsFromGroups.distinct()
        listDao.writeSync(
            type = type.name,
            run = run,
            media = rows.values.map { it.second }.distinctBy { it.id },
            entries = rows.values.map { it.first },
            customLists = names.mapIndexed { index, name -> CustomListEntity(type.name, name, index) },
            sync = ListSyncEntity(type.name, viewerId, clock.millis())
        )
        return Result.success(Unit)
    }

    override suspend fun plusOne(entryId: Int): PlusOneOutcome? {
        var completes = false
        val change = change(entryId) { entry ->
            ListEntryActions.plusOne(entry, today())?.also { completes = it.completes }?.changes
        } ?: return null
        return PlusOneOutcome(completed = completes, before = change.before)
    }

    override suspend fun start(entryId: Int) {
        change(entryId) { entry ->
            if (entry.status == MediaListStatus.PLANNING) ListEntryActions.start(today()) else null
        }
    }

    override suspend fun update(entryId: Int, changes: EntryChanges): Long? = change(entryId) { changes }?.id

    override suspend fun restore(entry: MediaListEntry) {
        change(entry.id) { current -> EntryChanges.between(current, entry) }
    }

    override suspend fun delete(entryId: Int) {
        val row = listDao.getEntry(entryId) ?: return
        database.withWriteTransaction {
            listDao.deleteEntry(entryId)
            mutationDao.insert(
                PendingMutationEntity(
                    entryId = entryId,
                    mediaId = row.entry.mediaId,
                    kind = MutationKind.DELETE,
                    changes = null,
                    previous = QueueJson.encodeToString(EntrySnapshot.serializer(), row.entry.toSnapshot()),
                    title = row.media?.titleUserPreferred.orEmpty(),
                    createdAt = clock.millis()
                )
            )
        }
        scheduler.sendQueuedChanges()
    }

    override fun observeChange(changeId: Long): Flow<ChangeState> = mutationDao.observe(changeId).map { row ->
        when {
            row == null -> ChangeState.Sent
            row.failureReason != null -> ChangeState.Rejected(row.toRejectedChange())
            else -> ChangeState.Queued
        }
    }

    override fun scheduleBackgroundSync() = scheduler.schedulePeriodicSync()

    private class Change(val id: Long, val before: MediaListEntry)

    /**
     * Applies the changes [build] returns to the entry in Room and queues them, in one transaction,
     * then asks for the queue to be sent. Null when the entry is missing or nothing changes.
     */
    private suspend fun change(entryId: Int, build: (MediaListEntry) -> EntryChanges?): Change? {
        val row = listDao.getEntry(entryId) ?: return null
        val before = row.toModel() ?: return null
        val requested = build(before) ?: return null
        // Only what really differs is stored and sent.
        val changes = EntryChanges.between(before, requested.applyTo(before)).takeUnless { it.isEmpty() } ?: return null
        val after = changes.applyTo(before).copy(updatedAt = clock.instant())
        val id = database.withWriteTransaction {
            listDao.upsertEntry(after.toEntity(syncRun = row.entry.syncRun))
            mutationDao.insert(
                PendingMutationEntity(
                    entryId = entryId,
                    mediaId = before.mediaId,
                    kind = MutationKind.SAVE,
                    changes = QueueJson.encodeToString(ChangesJson.serializer(), changes.toJson()),
                    previous = QueueJson.encodeToString(EntrySnapshot.serializer(), row.entry.toSnapshot()),
                    title = before.media.title.userPreferred,
                    createdAt = clock.millis()
                )
            )
        }
        scheduler.sendQueuedChanges()
        return Change(id, before)
    }

    private fun today(): LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
}

internal fun PendingMutationEntity.toRejectedChange() = RejectedChange(
    entryId = entryId,
    mediaId = mediaId,
    title = title,
    reason = enumOrNull<RejectReason>(failureReason) ?: RejectReason.Other,
    detail = failureDetail,
    fields = failureFields?.let { QueueJson.decodeFromString(FieldErrorsSerializer, it) }.orEmpty()
)
