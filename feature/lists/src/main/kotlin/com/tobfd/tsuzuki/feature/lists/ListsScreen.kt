package com.tobfd.tsuzuki.feature.lists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.ui.R as UiR
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderContent
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderLink
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderSamples
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/** Root of the Lists tab. */
@Serializable
data object ListsRoute : NavKey

/**
 * Lists tab content (placeholder until M4). Guests see a log-in state instead. The app shell draws the
 * top bar.
 */
@Composable
fun ListsScreen(
    isGuest: Boolean,
    onLogIn: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    if (isGuest) {
        EmptyState(
            icon = painterResource(TsuzukiIcons.List),
            title = stringResource(R.string.lists_guest_title),
            message = stringResource(R.string.lists_guest_message),
            actionLabel = stringResource(R.string.lists_log_in),
            onAction = onLogIn,
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    } else {
        PlaceholderContent(
            description = stringResource(R.string.lists_placeholder),
            links = persistentListOf(
                PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_TITLE)) {
                    onOpenMedia(PlaceholderSamples.FRIEREN_MEDIA_ID)
                }
            ),
            modifier = modifier,
            contentPadding = contentPadding
        )
    }
}

@ThemePreviews
@Composable
private fun ListsScreenPreview() {
    TsuzukiTheme {
        ListsScreen(isGuest = false, onLogIn = {}, onOpenMedia = {})
    }
}

@ThemePreviews
@Composable
private fun ListsScreenGuestPreview() {
    TsuzukiTheme {
        ListsScreen(isGuest = true, onLogIn = {}, onOpenMedia = {})
    }
}
