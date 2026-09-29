package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.list.ChangeState
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.list.PlusOneOutcome
import com.tobfd.tsuzuki.core.data.list.RejectedChange
import com.tobfd.tsuzuki.core.data.list.UserList
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.ListEntryActions
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ListRepository] that applies changes with the real rules from `core/model`, so view
 * models see realistic results. [today] is the date +1 and Start use.
 */
class FakeListRepository(
    entries: List<MediaListEntry> = emptyList(),
    private val customLists: Map<MediaType, List<String>> = emptyMap(),
    var today: LocalDate = LocalDate.of(2026, 9, 29)
) : ListRepository {

    val entries = MutableStateFlow(entries)
    val rejected = MutableStateFlow<List<RejectedChange>>(emptyList())

    /** State of each change returned by [update]; tests move it along. */
    val changeStates = mutableMapOf<Long, MutableStateFlow<ChangeState>>()

    var refreshResult: Result<Unit> = Result.success(Unit)
    val refreshCalls = mutableListOf<Boolean>()
    val deleted = mutableListOf<Int>()
    val updates = mutableListOf<Pair<Int, EntryChanges>>()
    var backgroundSyncScheduled = false
        private set
    private var nextChangeId = 1L

    override fun observeList(type: MediaType): Flow<UserList> = entries.map { all ->
        UserList(type, all.filter { it.type == type }, customLists[type].orEmpty())
    }

    override fun observeEntry(mediaId: Int): Flow<MediaListEntry?> =
        entries.map { all -> all.firstOrNull { it.mediaId == mediaId } }

    override val rejectedChanges: Flow<List<RejectedChange>> = rejected

    override suspend fun dismissRejection(entryId: Int) {
        rejected.value = rejected.value.filterNot { it.entryId == entryId }
    }

    val queued = MutableStateFlow(0)

    override val queuedChangeCount: Flow<Int> = queued

    override suspend fun refresh(force: Boolean): Result<Unit> {
        refreshCalls += force
        return refreshResult
    }

    override suspend fun plusOne(entryId: Int): PlusOneOutcome? {
        val entry = find(entryId) ?: return null
        val result = ListEntryActions.plusOne(entry, today) ?: return null
        replace(result.changes.applyTo(entry))
        return PlusOneOutcome(result.completes, entry)
    }

    override suspend fun start(entryId: Int) {
        val entry = find(entryId)?.takeIf { it.status == MediaListStatus.PLANNING } ?: return
        replace(ListEntryActions.start(today).applyTo(entry))
    }

    override suspend fun update(entryId: Int, changes: EntryChanges): Long? {
        val entry = find(entryId) ?: return null
        if (EntryChanges.between(entry, changes.applyTo(entry)).isEmpty()) return null
        updates += entryId to changes
        replace(changes.applyTo(entry))
        val id = nextChangeId++
        changeStates[id] = MutableStateFlow(ChangeState.Queued)
        return id
    }

    override suspend fun restore(entry: MediaListEntry) = replace(entry)

    override suspend fun delete(entryId: Int) {
        deleted += entryId
        entries.value = entries.value.filterNot { it.id == entryId }
    }

    override fun observeChange(changeId: Long): Flow<ChangeState> = changeStates.getValue(changeId)

    override fun scheduleBackgroundSync() {
        backgroundSyncScheduled = true
    }

    fun find(entryId: Int): MediaListEntry? = entries.value.firstOrNull { it.id == entryId }

    private fun replace(entry: MediaListEntry) {
        entries.value = entries.value.map { if (it.id == entry.id) entry else it }
    }
}
