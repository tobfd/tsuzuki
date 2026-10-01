package com.tobfd.tsuzuki.di

import com.tobfd.tsuzuki.BuildConfig
import com.tobfd.tsuzuki.core.common.AniListClientId
import com.tobfd.tsuzuki.core.common.AppBuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** From local.properties via BuildConfig; see README.md. */
    @Provides
    @AniListClientId
    fun aniListClientId(): String = BuildConfig.ANILIST_CLIENT_ID

    @Provides
    fun appBuildInfo(): AppBuildInfo = AppBuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        isDebug = BuildConfig.DEBUG,
        updateCheckEnabled = BuildConfig.UPDATE_CHECK
    )
}
