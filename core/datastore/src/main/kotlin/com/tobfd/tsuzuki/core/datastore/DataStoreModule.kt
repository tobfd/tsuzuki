package com.tobfd.tsuzuki.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tobfd.tsuzuki.core.common.ApplicationScope
import com.tobfd.tsuzuki.core.common.Dispatcher
import com.tobfd.tsuzuki.core.common.TsuzukiDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** File name of the session DataStore; excluded from backups (res/xml of `app`). */
internal const val SESSION_DATASTORE_NAME = "session"

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SessionPreferences

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @SessionPreferences
    fun sessionDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(TsuzukiDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(SESSION_DATASTORE_NAME) }
    )
}
