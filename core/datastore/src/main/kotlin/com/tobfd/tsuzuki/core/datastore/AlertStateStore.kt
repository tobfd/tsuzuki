package com.tobfd.tsuzuki.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the Android notifications remember between runs (docs/ROADMAP.md, Android notifications). */
data class AlertState(
    /** Episodes that aired up to here have been handled (notified or skipped). */
    val episodeCheckpoint: Instant? = null,
    /** AniList's unread count at the last check; null before the first check of a session. */
    val knownUnreadCount: Int? = null,
    /** The newest AniList notification already shown (or seen at the first check). */
    val newestNotificationId: Int? = null,
    /** The post-login hint about notifications was shown once; kept on logout. */
    val permissionHintShown: Boolean = false
)

/**
 * The Android notifications' bookkeeping in a Preferences DataStore of its own. Device state, so it is
 * not backed up; logout clears everything but [AlertState.permissionHintShown].
 */
@Singleton
class AlertStateStore @Inject constructor(@AlertPreferences private val dataStore: DataStore<Preferences>) {

    val state: Flow<AlertState> = dataStore.data.map { prefs ->
        AlertState(
            episodeCheckpoint = prefs[Keys.episodeCheckpoint]?.let(Instant::ofEpochSecond),
            knownUnreadCount = prefs[Keys.knownUnreadCount],
            newestNotificationId = prefs[Keys.newestNotificationId],
            permissionHintShown = prefs[Keys.permissionHintShown] ?: false
        )
    }

    suspend fun current(): AlertState = state.first()

    suspend fun setEpisodeCheckpoint(at: Instant) {
        dataStore.edit { it[Keys.episodeCheckpoint] = at.epochSecond }
    }

    suspend fun setNotificationsSeen(unreadCount: Int, newestId: Int?) {
        dataStore.edit { prefs ->
            prefs[Keys.knownUnreadCount] = unreadCount
            if (newestId != null) prefs[Keys.newestNotificationId] = newestId
        }
    }

    suspend fun setPermissionHintShown() {
        dataStore.edit { it[Keys.permissionHintShown] = true }
    }

    /** Logout: forgets the session's checkpoints, keeps that the hint was shown. */
    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.episodeCheckpoint)
            prefs.remove(Keys.knownUnreadCount)
            prefs.remove(Keys.newestNotificationId)
        }
    }

    private object Keys {
        val episodeCheckpoint = longPreferencesKey("episode_checkpoint")
        val knownUnreadCount = intPreferencesKey("known_unread_count")
        val newestNotificationId = intPreferencesKey("newest_notification_id")
        val permissionHintShown = booleanPreferencesKey("permission_hint_shown")
    }
}
