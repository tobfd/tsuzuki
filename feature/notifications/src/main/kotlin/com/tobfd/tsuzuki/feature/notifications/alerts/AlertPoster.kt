package com.tobfd.tsuzuki.feature.notifications.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.tobfd.tsuzuki.core.common.AppDestination
import com.tobfd.tsuzuki.core.common.AppLink
import com.tobfd.tsuzuki.core.common.AppTab
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import com.tobfd.tsuzuki.core.ui.notificationPlainText
import com.tobfd.tsuzuki.core.ui.notificationSubline
import com.tobfd.tsuzuki.feature.notifications.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Shows and removes the Android notifications. */
interface AlertPoster {
    fun postEpisodes(episodes: List<UpcomingEpisode>)

    fun postAniList(notifications: List<Notification>)

    /** Removes the AniList notifications, e.g. once the notifications screen shows them. */
    fun cancelAniList()

    fun cancelAll()
}

internal class SystemAlertPoster @Inject constructor(@ApplicationContext private val context: Context) : AlertPoster {
    private val manager = NotificationManagerCompat.from(context)

    override fun postEpisodes(episodes: List<UpcomingEpisode>) {
        if (episodes.isEmpty() || !canPost()) return
        episodes.forEach { upcoming ->
            val media = upcoming.entry.media
            val notification = builder(AlertChannel.Episodes)
                .setContentTitle(media.title.userPreferred)
                .setContentText(context.getString(R.string.alerts_episode_text, upcoming.episode))
                .setWhen(upcoming.airingAt.toEpochMilli())
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(open(AppDestination.Media(media.id, AppTab.Home)))
                .build()
            notify(TAG_EPISODE, media.id, notification)
        }
        postSummary(AlertChannel.Episodes)
    }

    override fun postAniList(notifications: List<Notification>) {
        if (notifications.isEmpty() || !canPost()) return
        val channels = mutableSetOf<AlertChannel>()
        notifications.forEach { item ->
            val channel = AlertChannel.of(item) ?: return@forEach
            channels += channel
            val builder = builder(channel)
                .setContentTitle(notificationPlainText(context.resources, item))
                .setWhen(item.createdAt.toEpochMilli())
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(open(item.destination()))
            notificationSubline(context.resources, item)?.let(builder::setContentText)
            notify(TAG_ANILIST, item.id, builder.build())
        }
        channels.forEach(::postSummary)
    }

    override fun cancelAniList() {
        manager.activeNotifications
            .filter { it.tag == TAG_ANILIST || (it.tag == TAG_SUMMARY && it.id != AlertChannel.Episodes.ordinal) }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    override fun cancelAll() {
        manager.cancelAll()
    }

    private fun Notification.destination(): AppDestination = when (this) {
        is Notification.Follow -> AppDestination.User(user.id, user.name)

        is Notification.MediaEvent ->
            media?.let { AppDestination.Media(it.id, AppTab.Home) } ?: AppDestination.Notifications

        is Notification.Airing -> AppDestination.Media(media.id, AppTab.Home)

        is Notification.ActivityEvent -> AppDestination.Notifications
    }

    private fun builder(channel: AlertChannel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_tsuzuki)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(channel.group)
            .setOnlyAlertOnce(true)

    /** The group's summary, so several notifications of a channel fold into one. */
    private fun postSummary(channel: AlertChannel) {
        val target = if (channel == AlertChannel.Episodes) {
            AppDestination.Tab(AppTab.Home)
        } else {
            AppDestination.Notifications
        }
        val summary = builder(channel)
            .setContentTitle(context.getString(channel.title))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(open(target))
            .build()
        notify(TAG_SUMMARY, channel.ordinal, summary)
    }

    /** A `tsuzuki://open/...` link to MainActivity, like the widgets use. */
    private fun open(destination: AppDestination): PendingIntent {
        val link = AppLink.uri(destination)
        val intent = Intent(Intent.ACTION_VIEW, link.toUri())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            link.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun notify(tag: String, id: Int, notification: android.app.Notification) {
        if (!canPost()) return
        try {
            manager.notify(tag, id, notification)
        } catch (e: SecurityException) {
            // The permission was revoked in between: nothing to show then.
        }
    }

    private companion object {
        const val TAG_EPISODE = "episode"
        const val TAG_ANILIST = "anilist"
        const val TAG_SUMMARY = "summary"
    }
}
