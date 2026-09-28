package com.tobfd.tsuzuki.feature.notifications

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
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

/** The viewer's notifications, opened from the bell. */
@Serializable
data object NotificationsRoute : NavKey

/** Notifications (placeholder until M10). */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenMedia: (Int) -> Unit, modifier: Modifier = Modifier) {
    NotificationsPlaceholderScaffold(
        title = stringResource(R.string.notifications_title),
        description = stringResource(R.string.notifications_placeholder),
        links = persistentListOf(
            PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_TITLE)) {
                onOpenMedia(PlaceholderSamples.FRIEREN_MEDIA_ID)
            }
        ),
        onBack = onBack,
        modifier = modifier
    )
}

/** Scaffold for a pushed placeholder screen: back top bar and [PlaceholderContent]. */
@Composable
internal fun NotificationsPlaceholderScaffold(
    title: String,
    description: String,
    links: ImmutableList<PlaceholderLink>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TsuzukiBackTopBar(title = title, onBack = onBack) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        PlaceholderContent(
            description = description,
            links = links,
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
private fun NotificationsScreenPreview() {
    TsuzukiTheme {
        NotificationsScreen(onBack = {}, onOpenMedia = {})
    }
}
