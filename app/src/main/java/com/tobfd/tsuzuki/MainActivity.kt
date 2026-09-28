package com.tobfd.tsuzuki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.ui.TsuzukiApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TsuzukiTheme {
                TsuzukiApp()
            }
        }
    }
}
