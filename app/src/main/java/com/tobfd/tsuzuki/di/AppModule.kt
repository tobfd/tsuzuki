package com.tobfd.tsuzuki.di

import com.tobfd.tsuzuki.BuildConfig
import com.tobfd.tsuzuki.core.common.AniListClientId
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
}
