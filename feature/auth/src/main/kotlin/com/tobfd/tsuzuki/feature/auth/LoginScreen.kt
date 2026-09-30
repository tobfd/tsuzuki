package com.tobfd.tsuzuki.feature.auth

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBanner
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiLogo
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.designsystem.theme.centeredMaxWidth
import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.ui.message

@Composable
fun LoginRoute(modifier: Modifier = Modifier, viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Opening the browser is a one-off UI effect; the ViewModel does the actual work.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.OpenAuthorizePage ->
                    if (!context.openCustomTab(effect.url)) viewModel.onBrowserUnavailable()
            }
        }
    }
    LoginScreen(
        uiState = uiState,
        onLogInClick = viewModel::onLogInClick,
        onBrowseAsGuestClick = viewModel::onBrowseAsGuestClick,
        modifier = modifier
    )
}

private fun Context.openCustomTab(url: String): Boolean = try {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, url.toUri())
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** Login as in docs/DESIGN.md: logo, name and tagline centered; login, guest and footnote at the bottom. */
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onLogInClick: () -> Unit,
    onBrowseAsGuestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        // Scrollable so text at 200 % never clips; the minimum height keeps the layout spread out.
        BoxWithConstraints(modifier = Modifier.safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.extraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(Modifier.height(0.dp))
                LoginHeader(logoutReason = uiState.logoutReason)
                LoginActions(
                    uiState = uiState,
                    onLogInClick = onLogInClick,
                    onBrowseAsGuestClick = onBrowseAsGuestClick
                )
            }
        }
    }
}

@Composable
private fun LoginHeader(logoutReason: LogoutReason?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TsuzukiLogo()
        Spacer(Modifier.height(TsuzukiSpacing.extraLarge))
        Text(
            text = stringResource(R.string.auth_app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(TsuzukiSpacing.small))
        Text(
            text = stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (logoutReason != null) {
            Spacer(Modifier.height(TsuzukiSpacing.extraLarge))
            TsuzukiBanner(message = logoutReason.message())
        }
    }
}

@Composable
private fun LoginActions(uiState: LoginUiState, onLogInClick: () -> Unit, onBrowseAsGuestClick: () -> Unit) {
    // On tablets the actions keep a phone's width instead of spanning the screen.
    Column(
        modifier = Modifier
            .centeredMaxWidth(TsuzukiSizes.formWidth)
            .padding(top = TsuzukiSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        uiState.error?.let { error ->
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = TsuzukiSpacing.medium)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Button(
            onClick = onLogInClick,
            enabled = !uiState.loggingIn,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TsuzukiSizes.primaryButtonHeight)
        ) {
            if (uiState.loggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TsuzukiSizes.icon),
                    color = LocalContentColor.current,
                    strokeWidth = TsuzukiSpacing.extraExtraSmall
                )
                Spacer(Modifier.size(TsuzukiSpacing.small))
                Text(stringResource(R.string.auth_logging_in))
            } else {
                Text(stringResource(R.string.auth_log_in))
            }
        }
        Spacer(Modifier.height(TsuzukiSpacing.small))
        TextButton(onClick = onBrowseAsGuestClick, enabled = !uiState.loggingIn) {
            Text(stringResource(R.string.auth_browse_as_guest))
        }
        Spacer(Modifier.height(TsuzukiSpacing.large))
        Text(
            text = stringResource(R.string.auth_unofficial_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LogoutReason.message(): String = stringResource(
    when (this) {
        LogoutReason.Expired -> R.string.auth_reason_expired
        LogoutReason.Unauthorized -> R.string.auth_reason_unauthorized
    }
)

@Composable
private fun LoginError.message(): String = when (this) {
    is LoginError.Api -> error.message()
    LoginError.Denied -> stringResource(R.string.auth_error_denied)
    LoginError.InvalidRedirect -> stringResource(R.string.auth_error_invalid_redirect)
    LoginError.NoBrowser -> stringResource(R.string.auth_error_no_browser)
}

@ThemePreviews
@Composable
private fun LoginScreenPreview() {
    TsuzukiTheme {
        LoginScreen(uiState = LoginUiState(), onLogInClick = {}, onBrowseAsGuestClick = {})
    }
}

@ThemePreviews
@Composable
private fun LoginScreenExpiredPreview() {
    TsuzukiTheme {
        LoginScreen(
            uiState = LoginUiState(logoutReason = LogoutReason.Expired, error = LoginError.Api(AppError.Offline)),
            onLogInClick = {},
            onBrowseAsGuestClick = {}
        )
    }
}

@ThemePreviews
@Composable
private fun LoginScreenLoggingInPreview() {
    TsuzukiTheme {
        LoginScreen(uiState = LoginUiState(loggingIn = true), onLogInClick = {}, onBrowseAsGuestClick = {})
    }
}
