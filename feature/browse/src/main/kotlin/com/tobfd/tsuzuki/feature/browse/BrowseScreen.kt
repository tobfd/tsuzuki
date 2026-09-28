package com.tobfd.tsuzuki.feature.browse

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

/** Root of the Browse tab. */
@Serializable
data object BrowseRoute : NavKey

/** Browse tab content (placeholder until M7). The app shell draws the top bar. */
@Composable
fun BrowseScreen(
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    PlaceholderContent(
        description = stringResource(R.string.browse_placeholder),
        links = persistentListOf(
            PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_TITLE)) {
                onOpenMedia(PlaceholderSamples.FRIEREN_MEDIA_ID)
            }
        ),
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@ThemePreviews
@Composable
private fun BrowseScreenPreview() {
    TsuzukiTheme {
        BrowseScreen(onOpenMedia = {})
    }
}
