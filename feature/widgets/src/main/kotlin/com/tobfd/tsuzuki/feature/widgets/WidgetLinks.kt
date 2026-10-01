package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.tobfd.tsuzuki.core.common.AppDestination
import com.tobfd.tsuzuki.core.common.AppLink

/** Opens [destination] in the app; restricted to the app's own package, so no other app can catch it. */
internal fun openAction(context: Context, destination: AppDestination): Action {
    val intent = Intent(Intent.ACTION_VIEW, AppLink.uri(destination).toUri())
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return actionStartActivity(intent)
}
