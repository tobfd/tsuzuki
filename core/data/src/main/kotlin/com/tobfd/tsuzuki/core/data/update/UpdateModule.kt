package com.tobfd.tsuzuki.core.data.update

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal interface UpdateModule {
    @Binds
    fun updateRepository(impl: DefaultUpdateRepository): UpdateRepository

    companion object {
        /** A client of its own, without any interceptor: nothing of the AniList session goes to GitHub. */
        @Provides
        @Singleton
        @GitHubHttpClient
        fun gitHubHttpClient(): OkHttpClient = gitHubClient()
    }
}

/** The GitHub client; a function so tests check exactly what the app uses. */
internal fun gitHubClient(): OkHttpClient = OkHttpClient.Builder()
    .callTimeout(Duration.ofSeconds(15))
    .build()
