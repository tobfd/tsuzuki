package com.tobfd.tsuzuki.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.tobfd.tsuzuki.core.database.entity.FriendActivityEntity
import kotlinx.coroutines.flow.Flow

/** The last fetched page of the followed users' activities (the "Friends' activity" widget). */
@Dao
abstract class FriendActivityDao {

    @Query("SELECT * FROM friend_activity ORDER BY created_at DESC, id DESC")
    abstract fun observeAll(): Flow<List<FriendActivityEntity>>

    @Query("DELETE FROM friend_activity")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertAll(activities: List<FriendActivityEntity>)

    /** Replaces the stored page with [activities] in one transaction. */
    @Transaction
    open suspend fun replaceAll(activities: List<FriendActivityEntity>) {
        deleteAll()
        insertAll(activities)
    }
}
