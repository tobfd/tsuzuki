package com.tobfd.tsuzuki.core.data.list

import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import javax.inject.Inject

/** Removes everything the app stored for the logged-in user, for logout. */
interface UserDataCleaner {
    suspend fun clear()
}

internal class DatabaseUserDataCleaner @Inject constructor(
    private val database: TsuzukiDatabase,
    private val scheduler: ListWorkScheduler
) : UserDataCleaner {
    override suspend fun clear() {
        scheduler.cancelAll()
        database.clearAllTables()
    }
}
