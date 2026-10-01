package com.tobfd.tsuzuki.core.data.notifications

import com.tobfd.tsuzuki.core.datastore.AlertStateStore
import com.tobfd.tsuzuki.core.model.Notification
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the Android notifications (docs/ROADMAP.md, Android notifications) need from the data layer:
 * their checkpoints and the check for new AniList notifications.
 */
interface AlertsRepository {
    /** Whether the post-login hint about notifications was shown; survives logout. */
    val permissionHintShown: Flow<Boolean>

    suspend fun markPermissionHintShown()

    /** Episodes that aired up to here were handled; null before the first plan of a session. */
    suspend fun episodeCheckpoint(): Instant?

    suspend fun setEpisodeCheckpoint(at: Instant)

    /**
     * AniList notifications that arrived since the last check, newest first, likes on one activity merged.
     * Asks for the unread count first (one small request) and loads the newest page only when the count
     * went up. The first check of a session only remembers the count, and so does any count the app showed
     * itself, so nothing the viewer already saw in the app is announced.
     */
    suspend fun newNotifications(): Result<List<Notification>>

    /** Logout: forgets the session's checkpoints. */
    suspend fun clear()
}

internal class DefaultAlertsRepository @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    private val store: AlertStateStore
) : AlertsRepository {

    override val permissionHintShown: Flow<Boolean> = store.state.map { it.permissionHintShown }

    override suspend fun markPermissionHintShown() = store.setPermissionHintShown()

    override suspend fun episodeCheckpoint(): Instant? = store.current().episodeCheckpoint

    override suspend fun setEpisodeCheckpoint(at: Instant) = store.setEpisodeCheckpoint(at)

    override suspend fun newNotifications(): Result<List<Notification>> {
        val unread = notificationsRepository.fetchUnreadCount().getOrElse { return Result.failure(it) }
        val state = store.current()
        val known = state.knownUnreadCount
        if (known == null || unread <= known) {
            store.setNotificationsSeen(unread, newestId = null)
            return Result.success(emptyList())
        }
        // The count stays as it was until the page arrived, so a failed load is tried again next time.
        val newest = notificationsRepository.newestNotifications().getOrElse { return Result.failure(it) }
        val seenUpTo = state.newestNotificationId
        val fresh = newest
            .take(unread - known)
            .filter { seenUpTo == null || it.id > seenUpTo }
        val newestId = (newest.map { it.id } + listOfNotNull(seenUpTo)).maxOrNull()
        store.setNotificationsSeen(unread, newestId)
        return Result.success(fresh)
    }

    override suspend fun clear() = store.clearSession()
}
