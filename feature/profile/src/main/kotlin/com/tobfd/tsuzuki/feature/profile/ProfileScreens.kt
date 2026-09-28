package com.tobfd.tsuzuki.feature.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.ui.R as UiR
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderContent
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderLink
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderSamples
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/** Root of the Profile tab: the viewer's own profile. */
@Serializable
data object ProfileRoute : NavKey

/** Another user's profile. */
@Serializable
data class UserRoute(val name: String) : NavKey

/**
 * Profile tab content (placeholder until M9). Guests see a log-in state instead. The app shell draws
 * the top bar.
 */
@Composable
fun ProfileScreen(
    isGuest: Boolean,
    onLogIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    if (isGuest) {
        EmptyState(
            icon = painterResource(TsuzukiIcons.Person),
            title = stringResource(R.string.profile_guest_title),
            message = stringResource(R.string.profile_guest_message),
            actionLabel = stringResource(R.string.profile_log_in),
            onAction = onLogIn,
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    } else {
        PlaceholderContent(
            description = stringResource(R.string.profile_placeholder),
            links = persistentListOf(
                PlaceholderLink(stringResource(R.string.profile_settings), onOpenSettings),
                PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.OTHER_USER_NAME)) {
                    onOpenUser(PlaceholderSamples.OTHER_USER_NAME)
                }
            ),
            modifier = modifier,
            contentPadding = contentPadding
        )
    }
}

/** Another user's profile (placeholder until M9). */
@Composable
fun UserScreen(userName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TsuzukiBackTopBar(title = userName, onBack = onBack) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        PlaceholderContent(
            description = stringResource(R.string.profile_user_placeholder),
            links = persistentListOf<PlaceholderLink>(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                end = innerPadding.calculateEndPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                top = innerPadding.calculateTopPadding() + TsuzukiSpacing.large,
                bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.large
            )
        )
    }
}

@ThemePreviews
@Composable
private fun ProfileScreenPreview() {
    TsuzukiTheme {
        ProfileScreen(isGuest = false, onLogIn = {}, onOpenSettings = {}, onOpenUser = {})
    }
}

@ThemePreviews
@Composable
private fun ProfileScreenGuestPreview() {
    TsuzukiTheme {
        ProfileScreen(isGuest = true, onLogIn = {}, onOpenSettings = {}, onOpenUser = {})
    }
}

@ThemePreviews
@Composable
private fun UserScreenPreview() {
    TsuzukiTheme {
        UserScreen(userName = PlaceholderSamples.OTHER_USER_NAME, onBack = {})
    }
}
