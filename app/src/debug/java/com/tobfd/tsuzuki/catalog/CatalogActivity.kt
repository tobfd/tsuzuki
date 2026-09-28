package com.tobfd.tsuzuki.catalog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.ThemeMode
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme

/** Debug-only catalog of every design system component in both color sources, light and dark. */
class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var colorSource by rememberSaveable { mutableStateOf(ColorSource.Dynamic) }
            var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

            // Keep status and navigation bar icons readable when the catalog overrides the system mode.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            TsuzukiTheme(
                colorSource = colorSource,
                themeMode = if (darkTheme) ThemeMode.Dark else ThemeMode.Light
            ) {
                CatalogScreen(
                    colorSource = colorSource,
                    darkTheme = darkTheme,
                    onColorSourceChange = { colorSource = it },
                    onDarkThemeChange = { darkTheme = it }
                )
            }
        }
    }
}
