package com.tobfd.tsuzuki.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.tobfd.tsuzuki.core.common.ApplicationScope
import com.tobfd.tsuzuki.core.common.Dispatcher
import com.tobfd.tsuzuki.core.common.TsuzukiDispatchers
import dagger.Binds
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

/** File name of the app settings DataStore (appearance); part of backups. */
internal const val SETTINGS_DATASTORE_NAME = "settings"

/** File name of the Android notifications' bookkeeping (AlertStateStore); excluded from backups. */
internal const val ALERTS_DATASTORE_NAME = "alerts"

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SessionPreferences

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SettingsPreferences

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AlertPreferences

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

    @Provides
    @Singleton
    @SettingsPreferences
    fun settingsDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(TsuzukiDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) }
    )

    @Provides
    @Singleton
    @AlertPreferences
    fun alertDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(TsuzukiDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(ALERTS_DATASTORE_NAME) }
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TokenEncryptionModule {
    @Binds
    fun tokenEncryption(impl: TokenCipher): TokenEncryption
}
