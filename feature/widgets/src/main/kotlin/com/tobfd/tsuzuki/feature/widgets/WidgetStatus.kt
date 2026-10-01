package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import com.tobfd.tsuzuki.core.common.AppError

/** Why a widget's last background update failed; shown under its content until one works again. */
internal enum class RefreshProblem {
    Offline,
    Failed;

    companion object {
        fun of(error: Throwable): RefreshProblem = if (error is AppError.Offline) Offline else Failed
    }
}

private val RefreshProblemKey = stringPreferencesKey("refresh_problem")

/** The problem of the last update, kept in each widget instance's Glance state. */
internal val currentRefreshProblem: RefreshProblem?
    @Composable get() = currentState<Preferences>()[RefreshProblemKey]
        ?.let { name -> RefreshProblem.entries.firstOrNull { it.name == name } }

/** Stores [problem] (null after a successful update) for every placed [widget] and redraws them. */
internal suspend fun GlanceAppWidget.setRefreshProblem(context: Context, problem: RefreshProblem?) {
    GlanceAppWidgetManager(context).getGlanceIds(javaClass).forEach { id ->
        updateAppWidgetState(context, id) { prefs ->
            if (problem == null) prefs.remove(RefreshProblemKey) else prefs[RefreshProblemKey] = problem.name
        }
        update(context, id)
    }
}

/** Whether at least one instance of [widget] is on a home screen. */
internal suspend fun GlanceAppWidget.isPlaced(context: Context): Boolean =
    GlanceAppWidgetManager(context).getGlanceIds(javaClass).isNotEmpty()
