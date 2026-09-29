package com.tobfd.tsuzuki.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** When a list type was last synced in full, for the 15-minute refresh rule. */
@Entity(tableName = "list_sync")
data class ListSyncEntity(
    @PrimaryKey val type: String,
    @ColumnInfo(name = "user_id") val userId: Int,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "synced_at") val syncedAt: Long
)
