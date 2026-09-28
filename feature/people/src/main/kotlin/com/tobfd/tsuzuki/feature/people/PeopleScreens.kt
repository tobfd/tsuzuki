package com.tobfd.tsuzuki.feature.people

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

/** Character page. */
@Serializable
data class CharacterRoute(val id: Int) : NavKey

/** Staff page (voice actors and production staff). */
@Serializable
data class StaffRoute(val id: Int) : NavKey

/** Character page (placeholder until M8). */
@Composable
fun CharacterScreen(
    characterId: Int,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenStaff: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    PeoplePlaceholderScaffold(
        title = stringResource(R.string.people_character_title),
        description = stringResource(R.string.people_character_placeholder, characterId),
        links = persistentListOf(
            PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_TITLE)) {
                onOpenMedia(PlaceholderSamples.FRIEREN_MEDIA_ID)
            },
            PlaceholderLink(
                stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_VOICE_ACTOR_NAME)
            ) {
                onOpenStaff(PlaceholderSamples.FRIEREN_VOICE_ACTOR_ID)
            }
        ),
        onBack = onBack,
        modifier = modifier
    )
}

/** Staff page (placeholder until M8). */
@Composable
fun StaffScreen(staffId: Int, onBack: () -> Unit, onOpenCharacter: (Int) -> Unit, modifier: Modifier = Modifier) {
    PeoplePlaceholderScaffold(
        title = stringResource(R.string.people_staff_title),
        description = stringResource(R.string.people_staff_placeholder, staffId),
        links = persistentListOf(
            PlaceholderLink(stringResource(UiR.string.ui_placeholder_open, PlaceholderSamples.FRIEREN_CHARACTER_NAME)) {
                onOpenCharacter(PlaceholderSamples.FRIEREN_CHARACTER_ID)
            }
        ),
        onBack = onBack,
        modifier = modifier
    )
}

/** Scaffold for a pushed placeholder screen: back top bar and [PlaceholderContent]. */
@Composable
internal fun PeoplePlaceholderScaffold(
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
private fun CharacterScreenPreview() {
    TsuzukiTheme {
        CharacterScreen(
            characterId = PlaceholderSamples.FRIEREN_CHARACTER_ID,
            onBack = {},
            onOpenMedia = {},
            onOpenStaff = {}
        )
    }
}

@ThemePreviews
@Composable
private fun StaffScreenPreview() {
    TsuzukiTheme {
        StaffScreen(staffId = PlaceholderSamples.FRIEREN_VOICE_ACTOR_ID, onBack = {}, onOpenCharacter = {})
    }
}
