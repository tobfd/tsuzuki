package com.tobfd.tsuzuki.core.database.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import com.tobfd.tsuzuki.core.database.dao.FriendActivityDao
import com.tobfd.tsuzuki.core.database.dao.MediaListDao
import com.tobfd.tsuzuki.core.database.dao.PendingMutationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** The framework's SQLite (`sqlite-framework`) instead of a bundled copy: smaller APK, same features here. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TsuzukiDatabase =
        Room.databaseBuilder(context, TsuzukiDatabase::class.java, TsuzukiDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun mediaListDao(database: TsuzukiDatabase): MediaListDao = database.mediaListDao()

    @Provides
    fun pendingMutationDao(database: TsuzukiDatabase): PendingMutationDao = database.pendingMutationDao()

    @Provides
    fun friendActivityDao(database: TsuzukiDatabase): FriendActivityDao = database.friendActivityDao()
}
