package com.tobfd.tsuzuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.tobfd.tsuzuki.R
import com.tobfd.tsuzuki.core.designsystem.component.InitialAvatar
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBanner
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiLogo
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions

/**
 * Temporary M2 screen: "Logged in as <name>" (or guest mode), the expiry warning and log out.
 * Replaced by the tab shell in M3.
 *
 * @param viewer null in guest mode.
 */
@Composable
fun SessionScreen(viewer: Viewer?, expiryWarningDays: Long?, onLogOut: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(TsuzukiSpacing.screenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)
        ) {
            if (viewer != null && expiryWarningDays != null) {
                TsuzukiBanner(
                    message = pluralStringResource(
                        R.plurals.session_expiry_warning,
                        expiryWarningDays.toInt(),
                        expiryWarningDays.toInt()
                    ),
                    actionLabel = stringResource(R.string.session_log_in_again),
                    onAction = onLogOut
                )
            }
            if (viewer != null) {
                InitialAvatar(name = viewer.name, modifier = Modifier.padding(top = TsuzukiSpacing.extraLarge))
            } else {
                TsuzukiLogo(
                    size = TsuzukiSizes.stateIconTile,
                    modifier = Modifier.padding(top = TsuzukiSpacing.extraLarge)
                )
            }
            Text(
                text = if (viewer != null) {
                    stringResource(R.string.session_logged_in_as, viewer.name)
                } else {
                    stringResource(R.string.session_guest)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedButton(onClick = onLogOut) {
                Text(stringResource(if (viewer != null) R.string.session_log_out else R.string.session_log_in))
            }
            Text(
                text = stringResource(R.string.session_placeholder_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val previewViewer = Viewer(
    id = 1,
    name = "tobfd",
    avatarUrl = null,
    options = ViewerOptions(
        titleLanguage = TitleLanguage.ROMAJI,
        staffNameLanguage = StaffNameLanguage.ROMAJI_WESTERN,
        displayAdultContent = false,
        scoreFormat = ScoreFormat.POINT_10_DECIMAL
    )
)

@ThemePreviews
@Composable
private fun SessionScreenLoggedInPreview() {
    TsuzukiTheme {
        SessionScreen(viewer = previewViewer, expiryWarningDays = 5, onLogOut = {})
    }
}

@ThemePreviews
@Composable
private fun SessionScreenGuestPreview() {
    TsuzukiTheme {
        SessionScreen(viewer = null, expiryWarningDays = null, onLogOut = {})
    }
}
