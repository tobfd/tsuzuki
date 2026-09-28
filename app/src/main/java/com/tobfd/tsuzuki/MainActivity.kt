package com.tobfd.tsuzuki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.feature.auth.AuthRedirects
import com.tobfd.tsuzuki.ui.TsuzukiApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRedirects: AuthRedirects

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value == MainUiState.Loading }
        // After a configuration change the redirect in the launch intent was already handled.
        if (savedInstanceState == null) handleAuthRedirect(intent)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LifecycleResumeEffect(viewModel) {
                viewModel.onAppResumed()
                onPauseOrDispose {}
            }
            TsuzukiTheme {
                TsuzukiApp(uiState = uiState, onLogOut = viewModel::onLogOut)
            }
        }
    }

    /** `launchMode="singleTask"`: the tsuzuki://auth redirect from the login Custom Tab arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent) {
        val uri = intent.dataString ?: return
        if (authRedirects.dispatch(uri)) {
            // The token must not linger in the intent.
            intent.data = null
        }
    }
}
