package com.tobfd.tsuzuki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tobfd.tsuzuki.MainUiState
import com.tobfd.tsuzuki.feature.auth.LoginRoute

/**
 * Root of the app UI: login when logged out, otherwise a temporary session screen until the
 * navigation shell arrives in M3.
 */
@Composable
fun TsuzukiApp(uiState: MainUiState, onLogOut: () -> Unit, modifier: Modifier = Modifier) {
    when (uiState) {
        // The splash screen stays up while loading; this only fills the window behind it.
        MainUiState.Loading -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))

        MainUiState.LoggedOut -> LoginRoute(modifier = modifier)

        MainUiState.Guest -> SessionScreen(
            viewer = null,
            expiryWarningDays = null,
            onLogOut = onLogOut,
            modifier = modifier
        )

        is MainUiState.LoggedIn -> SessionScreen(
            viewer = uiState.viewer,
            expiryWarningDays = uiState.expiryWarningDays,
            onLogOut = onLogOut,
            modifier = modifier
        )
    }
}
