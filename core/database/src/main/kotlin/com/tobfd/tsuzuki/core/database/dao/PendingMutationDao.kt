package com.tobfd.tsuzuki.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.tobfd.tsuzuki.core.database.entity.PendingMutationEntity
import kotlinx.coroutines.flow.Flow

/** The offline queue of list changes (`pending_mutation`). */
@Dao
interface PendingMutationDao {

    @Insert
    suspend fun insert(mutation: PendingMutationEntity): Long

    /** Changes still to send, oldest first. */
    @Query("SELECT * FROM pending_mutation WHERE failure_reason IS NULL ORDER BY id")
    suspend fun pending(): List<PendingMutationEntity>

    @Query("SELECT COUNT(*) FROM pending_mutation WHERE failure_reason IS NULL")
    fun observePendingCount(): Flow<Int>

    /** Changes AniList rejected, waiting for the user to see why. */
    @Query("SELECT * FROM pending_mutation WHERE failure_reason IS NOT NULL ORDER BY id")
    fun observeFailed(): Flow<List<PendingMutationEntity>>

    @Query("SELECT * FROM pending_mutation WHERE id = :id")
    fun observe(id: Long): Flow<PendingMutationEntity?>

    @Query("DELETE FROM pending_mutation WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** Drops the unsent changes of an entry, e.g. after an earlier one was rejected. */
    @Query("DELETE FROM pending_mutation WHERE entry_id = :entryId AND failure_reason IS NULL")
    suspend fun deletePending(entryId: Int)

    @Query("UPDATE pending_mutation SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun countAttempt(ids: List<Long>)

    @Query(
        "UPDATE pending_mutation SET failure_reason = :reason, failure_detail = :detail, " +
            "failure_fields = :fields WHERE id IN (:ids)"
    )
    suspend fun markFailed(ids: List<Long>, reason: String, detail: String?, fields: String?)

    @Query("DELETE FROM pending_mutation WHERE entry_id = :entryId AND failure_reason IS NOT NULL")
    suspend fun dismissFailures(entryId: Int)
}
