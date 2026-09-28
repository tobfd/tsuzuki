package com.tobfd.tsuzuki.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.ui.R as UiR
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderContent
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderLink
import com.tobfd.tsuzuki.core.ui.placeholder.PlaceholderSamples
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/** Root of the Home tab. */
@Serializable
data object HomeRoute : NavKey

/**
 * Home tab content (placeholder until M5). The app shell draws the top bar; [contentPadding] keeps the
 * content clear of it.
 */
@Composable
fun HomeScreen(
    onOpenMedia: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    PlaceholderContent(
        description = stringResource(R.string.home_placeholder),
        links = persistentListOf(
            PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_TITLE)) {
                onOpenMedia(PlaceholderSamples.FRIEREN_MEDIA_ID)
            },
            PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.OTHER_USER_NAME)) {
                onOpenUser(PlaceholderSamples.OTHER_USER_NAME)
            }
        ),
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@ThemePreviews
@Composable
private fun HomeScreenPreview() {
    TsuzukiTheme {
        HomeScreen(onOpenMedia = {}, onOpenUser = {})
    }
}
