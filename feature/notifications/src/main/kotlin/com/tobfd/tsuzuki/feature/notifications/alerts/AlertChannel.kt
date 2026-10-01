package com.tobfd.tsuzuki.feature.notifications.alerts

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.feature.notifications.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The Android notification channels. They are the only switches: the app has no settings of its own
 * per kind, Settings > Notifications opens the system page with these channels.
 */
enum class AlertChannel(val id: String, @StringRes val title: Int, @StringRes val description: Int) {
    Episodes("episodes", R.string.alerts_channel_episodes, R.string.alerts_channel_episodes_description),
    Activity("activity", R.string.alerts_channel_activity, R.string.alerts_channel_activity_description),
    Follows("follows", R.string.alerts_channel_follows, R.string.alerts_channel_follows_description),
    Other("other", R.string.alerts_channel_other, R.string.alerts_channel_other_description);

    /** Notifications of one channel are grouped together. */
    val group: String get() = "com.tobfd.tsuzuki.alerts.$id"

    companion object {
        /** The channels AniList's own notifications go to. */
        val aniList: Set<AlertChannel> = setOf(Activity, Follows, Other)

        /**
         * The channel of an AniList notification. Airing notifications have none: new episodes come from
         * the local plan, so AniList's copies would be duplicates.
         */
        fun of(notification: Notification): AlertChannel? = when (notification) {
            is Notification.Airing -> null
            is Notification.ActivityEvent -> Activity
            is Notification.Follow -> Follows
            is Notification.MediaEvent -> Other
        }
    }
}

/** Whether notifications of a channel would be shown: checked before every plan and every request. */
interface AlertGate {
    fun isEnabled(channel: AlertChannel): Boolean
}

internal class SystemAlertGate @Inject constructor(@ApplicationContext private val context: Context) : AlertGate {
    private val manager = NotificationManagerCompat.from(context)

    override fun isEnabled(channel: AlertChannel): Boolean {
        // False without POST_NOTIFICATIONS (Android 13+) or when the app's notifications are off.
        if (!manager.areNotificationsEnabled()) return false
        val systemChannel = manager.getNotificationChannelCompat(channel.id) ?: return true
        return systemChannel.importance != NotificationManagerCompat.IMPORTANCE_NONE
    }
}

/** Creates (or renames, after a language change) the channels; cheap and safe to call on every start. */
fun createAlertChannels(context: Context) {
    val channels = AlertChannel.entries.map { channel ->
        val importance = if (channel == AlertChannel.Other) {
            NotificationManagerCompat.IMPORTANCE_LOW
        } else {
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        }
        NotificationChannelCompat.Builder(channel.id, importance)
            .setName(context.getString(channel.title))
            .setDescription(context.getString(channel.description))
            .build()
    }
    NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
}
