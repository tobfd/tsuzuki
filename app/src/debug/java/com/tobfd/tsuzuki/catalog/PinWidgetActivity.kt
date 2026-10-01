package com.tobfd.tsuzuki.catalog

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle

/**
 * Debug only: asks the launcher to pin a widget, so emulator checks don't need dragging in the
 * widget picker. `adb shell am start -n com.tobfd.tsuzuki/.catalog.PinWidgetActivity --es widget next`
 * (`progress`, `next` or `friends`); the launcher then asks for confirmation.
 */
class PinWidgetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // By name: the app module doesn't see Glance, which the receivers extend.
        val receiver = when (intent.getStringExtra("widget")) {
            "next" -> "nextepisode.NextEpisodeWidgetReceiver"
            "friends" -> "friends.FriendActivityWidgetReceiver"
            else -> "inprogress.InProgressWidgetReceiver"
        }
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, "com.tobfd.tsuzuki.feature.widgets.$receiver"), null, null)
        }
        finish()
    }
}
