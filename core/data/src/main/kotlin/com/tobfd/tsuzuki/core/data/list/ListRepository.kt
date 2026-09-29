package com.tobfd.tsuzuki.core.data.list

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaType
import kotlinx.coroutines.flow.Flow

/** One list type of the viewer: every entry plus the names of the custom lists, in AniList's order. */
data class UserList(val type: MediaType, val entries: List<MediaListEntry>, val customLists: List<String>)

enum class RejectReason {
    /** AniList refused a value; [RejectedChange.fields] says which. */
    Validation,

    /** The entry is no longer on the list (removed on another device). */
    NotFound,

    /** Any other error that kept coming back. */
    Other
}

/** A change AniList rejected. The entry is already rolled back; the user still has to see why. */
data class RejectedChange(
    val entryId: Int,
    val mediaId: Int,
    val title: String,
    val reason: RejectReason,
    /** AniList's own message (English), if it sent one. */
    val detail: String?,
    /** AniList's field errors, e.g. `progress` → "The progress may not be greater than 28." */
    val fields: Map<String, List<String>>
)

/** Where a queued change is. */
sealed interface ChangeState {
    /** Waiting to be sent, e.g. while offline. */
    data object Queued : ChangeState

    /** AniList has it. */
    data object Sent : ChangeState

    data class Rejected(val change: RejectedChange) : ChangeState
}

/** What a +1 did, so the UI can play the right haptic and offer Undo. */
data class PlusOneOutcome(val completed: Boolean, val before: MediaListEntry)

/**
 * The viewer's anime and manga lists, offline first (CLAUDE.md, "Offline first for the user's own
 * lists"): reads come only from Room; every change is written to Room at once and queued, and a
 * worker sends the queue in order.
 */
interface ListRepository {
    fun observeList(type: MediaType): Flow<UserList>

    /** The viewer's entry for a media, or null when it isn't on the list. */
    fun observeEntry(mediaId: Int): Flow<MediaListEntry?>

    /** Changes AniList rejected; each stays until [dismissRejection]. */
    val rejectedChanges: Flow<List<RejectedChange>>

    suspend fun dismissRejection(entryId: Int)

    /** Number of changes not sent yet. */
    val queuedChangeCount: Flow<Int>

    /**
     * Sends queued changes, then syncs both lists from AniList. Without [force] a list that was
     * synced within the last 15 minutes is skipped. Fails with an [AppError] (e.g. offline); the
     * lists in Room stay as they are.
     */
    suspend fun refresh(force: Boolean): Result<Unit>

    /** +1 on an entry; null when +1 isn't possible (not watching, or already at the total). */
    suspend fun plusOne(entryId: Int): PlusOneOutcome?

    /** Starts a planned entry: watching (reading), progress 0, started today. */
    suspend fun start(entryId: Int)

    /** Saves [changes] to an entry; returns an id for [observeChange], or null when nothing changed. */
    suspend fun update(entryId: Int, changes: EntryChanges): Long?

    /** Puts an entry back as it was (Undo), queued like any other change. */
    suspend fun restore(entry: MediaListEntry)

    suspend fun delete(entryId: Int)

    fun observeChange(changeId: Long): Flow<ChangeState>

    /** Keeps the lists fresh in the background (every 6 hours) while someone is logged in. */
    fun scheduleBackgroundSync()
}
