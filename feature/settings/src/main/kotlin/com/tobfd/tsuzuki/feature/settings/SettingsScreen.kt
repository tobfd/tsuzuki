package com.tobfd.tsuzuki.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import kotlinx.serialization.Serializable

/** App and AniList settings, opened from the Profile tab. */
@Serializable
data object SettingsRoute : NavKey

/** Settings (placeholder until M11); keeps "Log out" from M2 reachable. */
@Composable
fun SettingsScreen(viewerName: String, onBack: () -> Unit, onLogOut: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TsuzukiBackTopBar(title = stringResource(R.string.settings_title), onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
        ) {
            Text(
                text = stringResource(R.string.settings_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_logged_in_as, viewerName),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(onClick = onLogOut) {
                Icon(painter = painterResource(TsuzukiIcons.Logout), contentDescription = null)
                Text(
                    text = stringResource(R.string.settings_log_out),
                    modifier = Modifier.padding(start = TsuzukiSpacing.small)
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsScreenPreview() {
    TsuzukiTheme {
        SettingsScreen(viewerName = "tobfd", onBack = {}, onLogOut = {})
    }
}
