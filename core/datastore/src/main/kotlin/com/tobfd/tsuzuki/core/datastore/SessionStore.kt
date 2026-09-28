package com.tobfd.tsuzuki.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Everything stored about the current session. The token is already decrypted. */
data class StoredSession(
    val accessToken: String? = null,
    val expiresAt: Instant? = null,
    val guest: Boolean = false,
    val logoutReason: LogoutReason? = null,
    val viewer: Viewer? = null,
    val viewerFetchedAt: Instant? = null
)

/**
 * The session in a Preferences DataStore: the encrypted token and its expiry, the guest flag, why the
 * last session ended, and the cached viewer (id, name, avatar and the AniList options the app follows).
 */
@Singleton
class SessionStore @Inject constructor(
    @SessionPreferences private val dataStore: DataStore<Preferences>,
    private val cipher: TokenEncryption
) {
    val session: Flow<StoredSession> = dataStore.data.map { it.toStoredSession() }

    suspend fun accessToken(): String? = dataStore.data.first()[Keys.token]?.let(cipher::decrypt)

    /** Stores a new token; clears guest mode and any old logout reason. */
    suspend fun saveToken(accessToken: String, expiresAt: Instant) {
        val encrypted = cipher.encrypt(accessToken)
        dataStore.edit { prefs ->
            prefs[Keys.token] = encrypted
            prefs[Keys.expiresAt] = expiresAt.epochSecond
            prefs.remove(Keys.guest)
            prefs.remove(Keys.logoutReason)
        }
    }

    suspend fun saveViewer(viewer: Viewer, fetchedAt: Instant) {
        dataStore.edit { prefs ->
            prefs[Keys.viewerId] = viewer.id
            prefs[Keys.viewerName] = viewer.name
            viewer.avatarUrl?.let { prefs[Keys.viewerAvatar] = it } ?: prefs.remove(Keys.viewerAvatar)
            prefs[Keys.titleLanguage] = viewer.options.titleLanguage.name
            prefs[Keys.staffNameLanguage] = viewer.options.staffNameLanguage.name
            prefs[Keys.displayAdultContent] = viewer.options.displayAdultContent
            prefs[Keys.scoreFormat] = viewer.options.scoreFormat.name
            prefs[Keys.viewerFetchedAt] = fetchedAt.epochSecond
        }
    }

    suspend fun enterGuestMode() {
        dataStore.edit { prefs ->
            prefs.clearSession()
            prefs[Keys.guest] = true
        }
    }

    /** Removes token, viewer and AniList options; keeps [reason] so the login screen can explain it. */
    suspend fun clear(reason: LogoutReason? = null) {
        dataStore.edit { prefs ->
            prefs.clearSession()
            reason?.let { prefs[Keys.logoutReason] = it.name }
        }
    }

    private fun MutablePreferences.clearSession() {
        Keys.all.forEach { remove(it) }
    }

    private fun Preferences.toStoredSession(): StoredSession = StoredSession(
        accessToken = this[Keys.token]?.let(cipher::decrypt),
        expiresAt = this[Keys.expiresAt]?.let(Instant::ofEpochSecond),
        guest = this[Keys.guest] ?: false,
        logoutReason = enumOrNull<LogoutReason>(this[Keys.logoutReason]),
        viewer = toViewer(),
        viewerFetchedAt = this[Keys.viewerFetchedAt]?.let(Instant::ofEpochSecond)
    )

    private fun Preferences.toViewer(): Viewer? {
        val id = this[Keys.viewerId] ?: return null
        val name = this[Keys.viewerName] ?: return null
        return Viewer(
            id = id,
            name = name,
            avatarUrl = this[Keys.viewerAvatar],
            options = ViewerOptions(
                titleLanguage = enumOrNull<TitleLanguage>(this[Keys.titleLanguage]) ?: TitleLanguage.ROMAJI,
                staffNameLanguage = enumOrNull<StaffNameLanguage>(this[Keys.staffNameLanguage])
                    ?: StaffNameLanguage.ROMAJI_WESTERN,
                displayAdultContent = this[Keys.displayAdultContent] ?: false,
                scoreFormat = enumOrNull<ScoreFormat>(this[Keys.scoreFormat]) ?: ScoreFormat.POINT_100
            )
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { value -> enumValues<E>().firstOrNull { it.name == value } }

    private object Keys {
        val token = stringPreferencesKey("access_token_encrypted")
        val expiresAt = longPreferencesKey("token_expires_at")
        val guest = booleanPreferencesKey("guest")
        val logoutReason = stringPreferencesKey("logout_reason")
        val viewerId = intPreferencesKey("viewer_id")
        val viewerName = stringPreferencesKey("viewer_name")
        val viewerAvatar = stringPreferencesKey("viewer_avatar")
        val titleLanguage = stringPreferencesKey("title_language")
        val staffNameLanguage = stringPreferencesKey("staff_name_language")
        val displayAdultContent = booleanPreferencesKey("display_adult_content")
        val scoreFormat = stringPreferencesKey("score_format")
        val viewerFetchedAt = longPreferencesKey("viewer_fetched_at")

        val all: List<Preferences.Key<*>> = listOf(
            token, expiresAt, guest, logoutReason, viewerId, viewerName, viewerAvatar, titleLanguage,
            staffNameLanguage, displayAdultContent, scoreFormat, viewerFetchedAt
        )
    }
}
