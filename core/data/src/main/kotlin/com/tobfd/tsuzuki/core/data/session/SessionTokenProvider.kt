package com.tobfd.tsuzuki.core.data.session

import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.network.auth.AccessTokenProvider
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Hands the stored token to `AuthInterceptor`. OkHttp calls this on its own background threads, so
 * blocking on DataStore (which keeps the data in memory after the first read) is fine.
 */
internal class SessionTokenProvider @Inject constructor(private val store: SessionStore) : AccessTokenProvider {
    override fun accessToken(): String? = runBlocking { store.accessToken() }
}
