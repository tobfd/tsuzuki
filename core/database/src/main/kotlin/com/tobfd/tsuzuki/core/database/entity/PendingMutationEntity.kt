package com.tobfd.tsuzuki.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A list change that is already in `media_list_entry` but not yet on AniList. A worker sends them
 * in [id] order; see `core/data`'s `ListMutationSender`.
 */
@Entity(tableName = "pending_mutation", indices = [Index(value = ["entry_id"])])
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Int,
    @ColumnInfo(name = "media_id") val mediaId: Int,
    /** `SAVE` or `DELETE`. */
    val kind: String,
    /** The changed fields as JSON (`SAVE` only). */
    val changes: String?,
    /** The whole entry before this change as JSON, to roll back if AniList rejects it. */
    val previous: String,
    /** Title for messages about this change, e.g. "Couldn't save Frieren". */
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val attempts: Int = 0,
    /** Set when AniList rejected the change; the row stays until the user has seen the message. */
    @ColumnInfo(name = "failure_message") val failureMessage: String? = null,
    /** Field errors from AniList as JSON, for the list editor. */
    @ColumnInfo(name = "failure_fields") val failureFields: String? = null
)
