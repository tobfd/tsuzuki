package com.tobfd.tsuzuki.core.network.di

import android.content.Context
import android.util.Log
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import com.tobfd.tsuzuki.core.network.BuildConfig
import com.tobfd.tsuzuki.core.network.auth.AuthInterceptor
import com.tobfd.tsuzuki.core.network.cache.Cache
import com.tobfd.tsuzuki.core.network.ratelimit.RateLimitInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

private const val ANILIST_URL = "https://graphql.anilist.co"
private const val MEMORY_CACHE_BYTES = 10 * 1024 * 1024
private const val CACHE_DB_NAME = "apollo.db"
private const val LOG_TAG = "AniListRateLimit"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun rateLimitInterceptor(): RateLimitInterceptor = RateLimitInterceptor(
        log = { message -> if (BuildConfig.DEBUG) Log.d(LOG_TAG, message) }
    )

    @Provides
    @Singleton
    fun okHttpClient(rateLimitInterceptor: RateLimitInterceptor, authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            // Rate limiting wraps auth, so a retry after a 429 goes through auth again.
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(authInterceptor)
            .build()

    @Provides
    @Singleton
    fun apolloClient(@ApplicationContext context: Context, okHttpClient: OkHttpClient): ApolloClient =
        // Cache (generated from extra.graphqls) registers the type policies with the normalized cache.
        with(Cache) {
            ApolloClient.Builder()
                .serverUrl(ANILIST_URL)
                .okHttpClient(okHttpClient)
                // Needed to tell "API temporarily disabled" (403) apart; AniListErrors closes the body.
                .httpExposeErrorBody(true)
                .cache(
                    MemoryCacheFactory(maxSizeBytes = MEMORY_CACHE_BYTES)
                        .chain(SqlNormalizedCacheFactory(context, CACHE_DB_NAME))
                )
                .build()
        }
}
