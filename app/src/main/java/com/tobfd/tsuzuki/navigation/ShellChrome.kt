package com.tobfd.tsuzuki.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.tobfd.tsuzuki.core.model.Viewer

/**
 * What the shell's top bars and banner show. Provided through a CompositionLocal because Navigation 3
 * keeps entries across recompositions, so entry content must read changing values instead of
 * capturing them.
 *
 * @param viewer null in guest mode (no bell, no avatar).
 */
@Immutable
data class ShellChrome(
    val viewer: Viewer? = null,
    val unreadNotificationCount: Int = 0,
    val expiryWarningDays: Long? = null
)

val LocalShellChrome = compositionLocalOf { ShellChrome() }
