package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.settings.SettingsRepository
import com.tobfd.tsuzuki.core.data.widget.AiringRepository
import com.tobfd.tsuzuki.core.data.widget.FriendActivityRepository
import com.tobfd.tsuzuki.core.model.SessionState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map

/**
 * What the widgets need from the app. Glance creates widgets, receivers and action callbacks itself,
 * so they reach Hilt's singletons through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetDependencies {
    fun listRepository(): ListRepository

    fun airingRepository(): AiringRepository

    fun friendActivityRepository(): FriendActivityRepository

    fun sessionRepository(): SessionRepository

    fun settingsRepository(): SettingsRepository

    fun widgetUpdater(): WidgetUpdater
}

internal fun Context.widgetDependencies(): WidgetDependencies =
    EntryPointAccessors.fromApplication(applicationContext, WidgetDependencies::class.java)

/** Whether someone is logged in; guests and logged-out users see the log-in state. */
internal val SessionRepository.isLoggedIn: Flow<Boolean>
    get() = session.filterNot { it is SessionState.Loading }
        .map { it is SessionState.LoggedIn }
        .distinctUntilChanged()
