package com.tobfd.tsuzuki.core.database

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.tobfd.tsuzuki.core.database.dao.FriendActivityDao
import com.tobfd.tsuzuki.core.database.dao.MediaListDao
import com.tobfd.tsuzuki.core.database.dao.PendingMutationDao
import com.tobfd.tsuzuki.core.database.entity.CustomListEntity
import com.tobfd.tsuzuki.core.database.entity.FriendActivityEntity
import com.tobfd.tsuzuki.core.database.entity.ListSyncEntity
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.database.entity.PendingMutationEntity

/**
 * The app's own data: the viewer's lists (offline first) and the queue of list changes. Everything
 * in it belongs to the logged-in user, so logout clears all tables.
 *
 * Version 2 (widgets) adds `media_lite.next_airing_at` and the `friend_activity` table.
 */
@Database(
    entities = [
        MediaListEntryEntity::class,
        MediaLiteEntity::class,
        CustomListEntity::class,
        PendingMutationEntity::class,
        ListSyncEntity::class,
        FriendActivityEntity::class
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class TsuzukiDatabase : RoomDatabase() {
    abstract fun mediaListDao(): MediaListDao

    abstract fun pendingMutationDao(): PendingMutationDao

    abstract fun friendActivityDao(): FriendActivityDao

    companion object {
        const val NAME = "tsuzuki.db"
    }
}
