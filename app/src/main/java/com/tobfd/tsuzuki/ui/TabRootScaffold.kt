package com.tobfd.tsuzuki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tobfd.tsuzuki.R
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBanner
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiTopBar
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import com.tobfd.tsuzuki.core.ui.UserAvatar
import com.tobfd.tsuzuki.navigation.LocalShellChrome
import com.tobfd.tsuzuki.navigation.ShellChrome

/**
 * Frame of a tab's root screen: [TsuzukiTopBar] with the tab title, bell and avatar (none for guests),
 * the login expiry banner when due, then [content] with padding that keeps it clear of the bars.
 */
@Composable
fun TabRootScaffold(
    title: String,
    onNotificationsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onRenewLogin: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    val chrome = LocalShellChrome.current
    val viewer = chrome.viewer
    Scaffold(
        modifier = modifier,
        topBar = {
            TsuzukiTopBar(
                title = title,
                unreadNotificationCount = if (viewer != null) chrome.unreadNotificationCount else null,
                onNotificationsClick = onNotificationsClick,
                avatar = viewer?.let { { UserAvatar(avatarUrl = it.avatarUrl, name = it.name) } },
                onAvatarClick = onAvatarClick,
                actions = actions
            )
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
            chrome.expiryWarningDays?.let { days ->
                TsuzukiBanner(
                    message = pluralStringResource(R.plurals.session_expiry_warning, days.toInt(), days.toInt()),
                    actionLabel = stringResource(R.string.session_log_in_again),
                    onAction = onRenewLogin,
                    modifier = Modifier.padding(
                        horizontal = TsuzukiSpacing.screenMargin,
                        vertical = TsuzukiSpacing.small
                    )
                )
            }
            content(
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                    end = innerPadding.calculateEndPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                    top = TsuzukiSpacing.large,
                    bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.large
                )
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
private fun TabRootScaffoldPreview() {
    TsuzukiTheme {
        CompositionLocalProvider(
            LocalShellChrome provides
                ShellChrome(viewer = previewViewer, unreadNotificationCount = 3, expiryWarningDays = 5)
        ) {
            TabRootScaffold(title = "Home", onNotificationsClick = {
            }, onAvatarClick = {}, onRenewLogin = {}) { padding ->
                Text("Content", modifier = Modifier.padding(padding))
            }
        }
    }
}
