package com.tobfd.tsuzuki.core.common

import javax.inject.Qualifier

/**
 * The AniList API client ID (a `String`), provided by `app` from `BuildConfig.ANILIST_CLIENT_ID`,
 * which comes from `local.properties`.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AniListClientId
