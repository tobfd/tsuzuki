package com.tobfd.tsuzuki

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tobfd.tsuzuki.core.common.AppLink
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.ThemeMode
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.feature.auth.AuthRedirects
import com.tobfd.tsuzuki.feature.notifications.alerts.AlertCoordinator
import com.tobfd.tsuzuki.ui.TsuzukiApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRedirects: AuthRedirects

    @Inject
    lateinit var alertCoordinator: AlertCoordinator

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            viewModel.uiState.value == MainUiState.Loading || viewModel.appearance.value == null
        }
        // After a configuration change the redirect or link in the launch intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val destination by viewModel.destination.collectAsStateWithLifecycle()
            LifecycleResumeEffect(viewModel) {
                viewModel.onAppResumed()
                // Permissions or channels may have changed outside the app.
                lifecycleScope.launch { alertCoordinator.replan() }
                onPauseOrDispose {}
            }
            val appearance = viewModel.appearance.collectAsStateWithLifecycle().value ?: AppearanceSettings()
            val themeMode = appearance.themeMode.toThemeMode()
            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // The app's theme mode can differ from the system's: keep the bar icons readable.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TsuzukiTheme(
                colorSource = appearance.colors.toColorSource(),
                themeMode = themeMode,
                pureBlack = appearance.pureBlack
            ) {
                TsuzukiApp(
                    uiState = uiState,
                    onLogOut = viewModel::onLogOut,
                    destination = destination,
                    onDestinationOpened = viewModel::onDestinationOpened
                )
            }
        }
    }

    /**
     * `launchMode="singleTask"`: the tsuzuki://auth redirect from the login Custom Tab and the
     * widgets' tsuzuki://open links arrive here while the app runs.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.dataString ?: return
        AppLink.parse(uri)?.let {
            viewModel.open(it)
            return
        }
        if (authRedirects.dispatch(uri)) {
            // The token must not linger in the intent.
            intent.data = null
        }
    }
}

private fun AppColors.toColorSource(): ColorSource = when (this) {
    AppColors.MaterialYou -> ColorSource.Dynamic
    AppColors.AniListBlue -> ColorSource.AniListBlue
}

private fun AppThemeMode.toThemeMode(): ThemeMode = when (this) {
    AppThemeMode.System -> ThemeMode.System
    AppThemeMode.Light -> ThemeMode.Light
    AppThemeMode.Dark -> ThemeMode.Dark
}
