package com.tobfd.tsuzuki.core.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.ui.ScrollToTopOnTabReselect
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A button on a placeholder screen that opens another destination. */
@Immutable
data class PlaceholderLink(val label: String, val onClick: () -> Unit)

/**
 * Temporary content for screens that are not built yet (M3): what the screen will show and links to
 * other destinations so every route can be reached. Each feature replaces it in its milestone.
 */
@Composable
fun PlaceholderContent(
    description: String,
    links: ImmutableList<PlaceholderLink>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val listState = rememberLazyListState()
    ScrollToTopOnTabReselect(listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
    ) {
        item {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(links, key = { it.label }) { link ->
            OutlinedButton(onClick = link.onClick) {
                Text(link.label)
            }
        }
    }
}

/** Real AniList ids used by the placeholder links (Frieren and people from it). */
object PlaceholderSamples {
    const val FRIEREN_MEDIA_ID = 154_587
    const val FRIEREN_TITLE = "Sousou no Frieren"
    const val FRIEREN_CHARACTER_ID = 176_754
    const val FRIEREN_CHARACTER_NAME = "Frieren"
    const val FRIEREN_VOICE_ACTOR_ID = 112_215
    const val FRIEREN_VOICE_ACTOR_NAME = "Atsumi Tanezaki"
    const val OTHER_USER_NAME = "GeckoTV"
}

@ThemePreviews
@Composable
private fun PlaceholderContentPreview() {
    TsuzukiPreview {
        PlaceholderContent(
            description = "Media details arrive in M6.",
            links = persistentListOf(PlaceholderLink("Open Frieren") {}, PlaceholderLink("Open Atsumi Tanezaki") {})
        )
    }
}
