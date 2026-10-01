package com.tobfd.tsuzuki.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the GitHub update check remembers; device settings, so logout keeps them. */
data class UpdateState(
    val autoCheck: Boolean = true,
    val lastCheck: Instant? = null,
    /** The newest release found at the last check, if it was newer than the installed build. */
    val latestVersion: String? = null,
    val latestUrl: String? = null,
    /** The version whose hint on Home was closed or used; it isn't shown again. */
    val dismissedVersion: String? = null
)

@Singleton
class UpdateStore @Inject constructor(@SettingsPreferences private val dataStore: DataStore<Preferences>) {

    val state: Flow<UpdateState> = dataStore.data.map { prefs ->
        UpdateState(
            autoCheck = prefs[Keys.autoCheck] ?: true,
            lastCheck = prefs[Keys.lastCheck]?.let(Instant::ofEpochSecond),
            latestVersion = prefs[Keys.latestVersion],
            latestUrl = prefs[Keys.latestUrl],
            dismissedVersion = prefs[Keys.dismissedVersion]
        )
    }

    suspend fun current(): UpdateState = state.first()

    suspend fun setAutoCheck(enabled: Boolean) {
        dataStore.edit { it[Keys.autoCheck] = enabled }
    }

    /** A finished check at [at]: [version] and [url] of a newer release, or nulls when there is none. */
    suspend fun setChecked(at: Instant, version: String?, url: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.lastCheck] = at.epochSecond
            if (version != null && url != null) {
                prefs[Keys.latestVersion] = version
                prefs[Keys.latestUrl] = url
            } else {
                prefs.remove(Keys.latestVersion)
                prefs.remove(Keys.latestUrl)
            }
        }
    }

    suspend fun setDismissed(version: String) {
        dataStore.edit { it[Keys.dismissedVersion] = version }
    }

    private object Keys {
        val autoCheck = booleanPreferencesKey("update_auto_check")
        val lastCheck = longPreferencesKey("update_last_check")
        val latestVersion = stringPreferencesKey("update_latest_version")
        val latestUrl = stringPreferencesKey("update_latest_url")
        val dismissedVersion = stringPreferencesKey("update_dismissed_version")
    }
}
