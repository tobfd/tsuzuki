package com.tobfd.tsuzuki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.tobfd.tsuzuki.MainUiState
import com.tobfd.tsuzuki.core.common.AppDestination
import com.tobfd.tsuzuki.feature.auth.LoginRoute
import com.tobfd.tsuzuki.navigation.ShellChrome

/**
 * Root of the app UI: the login screen when logged out, otherwise the tab shell. The shell opens
 * [destination] (from a widget) and reports it with [onDestinationOpened]; while logged out it waits
 * for the login.
 */
@Composable
fun TsuzukiApp(
    uiState: MainUiState,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
    destination: AppDestination? = null,
    onDestinationOpened: () -> Unit = {}
) {
    when (uiState) {
        // The splash screen stays up while loading; this only fills the window behind it.
        MainUiState.Loading -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))

        MainUiState.LoggedOut -> LoginRoute(modifier = modifier)

        // Guest and logged-in shells never share state: switching goes through the login screen.
        MainUiState.Guest -> key(MainUiState.Guest) {
            AppShell(
                chrome = ShellChrome(),
                onLogOut = onLogOut,
                destination = destination,
                onDestinationOpened = onDestinationOpened,
                modifier = modifier
            )
        }

        is MainUiState.LoggedIn -> key(uiState.viewer.id) {
            AppShell(
                chrome = ShellChrome(
                    viewer = uiState.viewer,
                    unreadNotificationCount = uiState.unreadNotificationCount,
                    expiryWarningDays = uiState.expiryWarningDays
                ),
                onLogOut = onLogOut,
                destination = destination,
                onDestinationOpened = onDestinationOpened,
                modifier = modifier
            )
        }
    }
}
