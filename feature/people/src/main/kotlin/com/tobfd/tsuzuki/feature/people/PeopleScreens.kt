package com.tobfd.tsuzuki.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.ProgressButton
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.CharacterAppearance
import com.tobfd.tsuzuki.core.model.CharacterDetail
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.PersonLite
import com.tobfd.tsuzuki.core.model.PersonName
import com.tobfd.tsuzuki.core.model.ProductionRole
import com.tobfd.tsuzuki.core.model.StaffDetail
import com.tobfd.tsuzuki.core.model.VoicedCharacter
import com.tobfd.tsuzuki.core.ui.AniListHtmlText
import com.tobfd.tsuzuki.core.ui.CoverCardWidth
import com.tobfd.tsuzuki.core.ui.MediaCover
import com.tobfd.tsuzuki.core.ui.MediaCoverCard
import com.tobfd.tsuzuki.core.ui.PersonCoverCard
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.characterRoleLabel
import com.tobfd.tsuzuki.core.ui.formatDate
import com.tobfd.tsuzuki.core.ui.mediaMeta
import com.tobfd.tsuzuki.core.ui.message
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Character page. */
@Serializable
data class CharacterRoute(val id: Int) : NavKey

/** Staff page (voice actors and production staff). */
@Serializable
data class StaffRoute(val id: Int) : NavKey

private val HeaderImageWidth = 112.dp

/** Character page (docs/DESIGN.md, Character / Staff): one request, appearances with "Load more". */
@Composable
fun CharacterScreen(
    characterId: Int,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<CharacterViewModel, CharacterViewModel.Factory>(
        key = "character-$characterId",
        creationCallback = { it.create(characterId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appearances by viewModel.appearances.collectAsStateWithLifecycle()
    CharacterContent(
        state = state,
        appearances = appearances,
        events = viewModel.eventFlow,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onToggleFavourite = viewModel::onToggleFavourite,
        onLoadMore = viewModel::onLoadMoreAppearances,
        onOpenMedia = onOpenMedia,
        onLogIn = onLogIn,
        modifier = modifier
    )
}

@Composable
internal fun CharacterContent(
    state: PersonUiState<CharacterDetail>,
    appearances: PagedItems<CharacterAppearance>,
    events: Flow<PersonEvent>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavourite: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    PersonPage(
        state = state,
        fallbackTitle = stringResource(R.string.people_character_title),
        title = { it.name.userPreferred },
        events = events,
        onBack = onBack,
        onRetry = onRetry,
        onLogIn = onLogIn,
        modifier = modifier
    ) { content ->
        val character = content.detail
        fullWidth("header") {
            PersonHeader(
                imageUrl = character.imageUrl,
                name = character.name,
                subtitle = null,
                favourites = character.favourites,
                isFavourite = content.isFavourite,
                siteUrl = character.siteUrl,
                onToggleFavourite = onToggleFavourite
            )
        }
        fullWidth("info") {
            InfoRows(
                listOfNotNull(
                    character.gender?.let { stringResource(R.string.people_info_gender) to it },
                    character.age?.let { stringResource(R.string.people_info_age) to it },
                    character.dateOfBirth?.let { stringResource(R.string.people_info_birthday) to birthday(it) },
                    character.bloodType?.let { stringResource(R.string.people_info_blood_type) to it }
                )
            )
        }
        character.descriptionHtml?.let { html -> fullWidth("description") { AniListHtmlText(html) } }
        fullWidth("appearancesTitle") { SectionTitle(stringResource(R.string.people_appearances)) }
        if (appearances.items.isEmpty()) {
            fullWidth("noAppearances") {
                Text(
                    text = stringResource(R.string.people_no_appearances),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(appearances.items, key = { "media-${it.media.id}" }) { appearance ->
            MediaCoverCard(
                media = appearance.media,
                onClick = { onOpenMedia(appearance.media.id) },
                label = appearance.role?.let { characterRoleLabel(it) },
                meta = appearance.voiceActor?.name ?: mediaMeta(appearance.media)
            )
        }
        loadMore("moreAppearances", appearances, onLoadMore)
    }
}

/** Staff page: characters voiced and production roles, each with "Load more". */
@Composable
fun StaffScreen(
    staffId: Int,
    onBack: () -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenMedia: (Int) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<StaffViewModel, StaffViewModel.Factory>(
        key = "staff-$staffId",
        creationCallback = { it.create(staffId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    StaffContent(
        state = state,
        characters = characters,
        roles = roles,
        events = viewModel.eventFlow,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onToggleFavourite = viewModel::onToggleFavourite,
        onLoadMoreCharacters = viewModel::onLoadMoreCharacters,
        onLoadMoreRoles = viewModel::onLoadMoreRoles,
        onOpenCharacter = onOpenCharacter,
        onOpenMedia = onOpenMedia,
        onLogIn = onLogIn,
        modifier = modifier
    )
}

@Composable
internal fun StaffContent(
    state: PersonUiState<StaffDetail>,
    characters: PagedItems<VoicedCharacter>,
    roles: PagedItems<ProductionRole>,
    events: Flow<PersonEvent>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavourite: () -> Unit,
    onLoadMoreCharacters: () -> Unit,
    onLoadMoreRoles: () -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenMedia: (Int) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    PersonPage(
        state = state,
        fallbackTitle = stringResource(R.string.people_staff_title),
        title = { it.name.userPreferred },
        events = events,
        onBack = onBack,
        onRetry = onRetry,
        onLogIn = onLogIn,
        modifier = modifier
    ) { content ->
        val staff = content.detail
        fullWidth("header") {
            PersonHeader(
                imageUrl = staff.imageUrl,
                name = staff.name,
                subtitle = staff.occupations.joinToString(" · ").takeIf { it.isNotEmpty() },
                favourites = staff.favourites,
                isFavourite = content.isFavourite,
                siteUrl = staff.siteUrl,
                onToggleFavourite = onToggleFavourite
            )
        }
        fullWidth("info") {
            InfoRows(
                listOfNotNull(
                    staff.gender?.let { stringResource(R.string.people_info_gender) to it },
                    staff.age?.let { stringResource(R.string.people_info_age) to it.toString() },
                    staff.dateOfBirth?.let { stringResource(R.string.people_info_birthday) to birthday(it) },
                    staff.homeTown?.let { stringResource(R.string.people_info_hometown) to it },
                    yearsActive(staff.yearsActive)?.let { stringResource(R.string.people_info_years_active) to it }
                )
            )
        }
        staff.descriptionHtml?.let { html -> fullWidth("description") { AniListHtmlText(html) } }
        if (characters.items.isNotEmpty()) {
            fullWidth("charactersTitle") { SectionTitle(stringResource(R.string.people_characters_voiced)) }
            items(characters.items, key = { "character-${it.character.id}" }) { voiced ->
                PersonCoverCard(
                    person = voiced.character,
                    onClick = { onOpenCharacter(voiced.character.id) },
                    detail = voiced.media?.title?.userPreferred
                )
            }
            loadMore("moreCharacters", characters, onLoadMoreCharacters)
        }
        if (roles.items.isNotEmpty()) {
            fullWidth("rolesTitle") { SectionTitle(stringResource(R.string.people_production_roles)) }
            items(roles.items, key = { "role-${it.media.id}-${it.role}" }) { role ->
                MediaCoverCard(
                    media = role.media,
                    onClick = { onOpenMedia(role.media.id) },
                    label = role.role,
                    meta = mediaMeta(role.media)
                )
            }
            loadMore("moreRoles", roles, onLoadMoreRoles)
        }
    }
}

/**
 * Frame of both pages: back top bar with the name, loading and error states, the snackbar for
 * failed actions and the log-in prompt, and a grid of covers below full-width header items.
 */
@Composable
private fun <T : Any> PersonPage(
    state: PersonUiState<T>,
    fallbackTitle: String,
    title: (T) -> String,
    events: Flow<PersonEvent>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyGridScope.(PersonUiState.Content<T>) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                PersonEvent.LogInToUse -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.people_log_in_to_use),
                        actionLabel = resources.getString(R.string.people_log_in)
                    )
                    if (result == SnackbarResult.ActionPerformed) onLogIn()
                }

                is PersonEvent.Failed -> failure = event.error
            }
        }
    }
    failure?.let { error ->
        val message = error.message()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            failure = null
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TsuzukiBackTopBar(
                title = (state as? PersonUiState.Content)?.let { title(it.detail) } ?: fallbackTitle,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (state) {
            PersonUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }

            is PersonUiState.Error -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                ErrorState(
                    title = stringResource(R.string.people_error_title),
                    message = state.error.message(),
                    onRetry = onRetry
                )
            }

            is PersonUiState.Content -> {
                val layoutDirection = LocalLayoutDirection.current
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(CoverCardWidth),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = innerPadding.calculateStartPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                        end = innerPadding.calculateEndPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                        top = innerPadding.calculateTopPadding() + TsuzukiSpacing.small,
                        bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.extraLarge
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)
                ) {
                    content(state)
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun <T> LazyGridScope.loadMore(key: String, list: PagedItems<T>, onLoadMore: () -> Unit) {
    if (list.nextPage == null) return
    fullWidth(key) {
        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            ProgressButton(
                text = stringResource(R.string.people_load_more),
                loading = list.loading,
                onClick = onLoadMore
            )
        }
    }
}

@Composable
private fun PersonHeader(
    imageUrl: String?,
    name: PersonName,
    subtitle: String?,
    favourites: Int?,
    isFavourite: Boolean,
    siteUrl: String?,
    onToggleFavourite: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)) {
        MediaCover(imageUrl = imageUrl, contentDescription = null, modifier = Modifier.width(HeaderImageWidth))
        Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
            Text(text = name.userPreferred, style = MaterialTheme.typography.headlineMedium)
            val otherNames = listOfNotNull(name.full, name.native)
                .filter { it.isNotBlank() && it != name.userPreferred }
                .joinToString(" · ")
            if (otherNames.isNotEmpty()) {
                Text(
                    text = otherNames,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (name.alternative.isNotEmpty()) {
                Text(
                    text = name.alternative.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = stringResource(if (isFavourite) R.string.people_unfavourite else R.string.people_favourite)
                val scheme = MaterialTheme.colorScheme
                IconToggleButton(checked = isFavourite, onCheckedChange = { onToggleFavourite() }) {
                    Icon(
                        painter = painterResource(
                            if (isFavourite) TsuzukiIcons.FavoriteFilled else TsuzukiIcons.Favorite
                        ),
                        contentDescription = label,
                        tint = if (isFavourite) scheme.error else scheme.onSurfaceVariant
                    )
                }
                favourites?.let {
                    Text(
                        text = pluralStringResource(R.plurals.people_favourites, it, "%,d".format(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (siteUrl != null) {
                    IconButton(onClick = { uriHandler.openUri(siteUrl) }) {
                        Icon(
                            painterResource(TsuzukiIcons.OpenInNew),
                            contentDescription = stringResource(R.string.people_open_on_anilist)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(top = TsuzukiSpacing.small)
            .semantics { heading() }
    )
}

/** Birthdays often lack the year; "18 Mar" then. */
@Composable
private fun birthday(date: FuzzyDate): String {
    val month = date.month
    val day = date.day
    if (date.year != null || month == null || day == null) return formatDate(date)
    val locale = LocalConfiguration.current.locales[0]
    return MonthDay.of(month, day).format(DateTimeFormatter.ofPattern("d MMM", locale))
}

@Composable
private fun yearsActive(years: List<Int>): String? = when {
    years.size >= 2 -> stringResource(R.string.people_years_range, years[0], years[1])
    years.size == 1 -> stringResource(R.string.people_years_since, years[0])
    else -> null
}

private val previewCharacter = CharacterDetail(
    id = 176754,
    name = PersonName("Frieren", "Frieren", "フリーレン", listOf("The Slayer")),
    imageUrl = null,
    descriptionHtml = "An elf mage who once defeated the Demon King. <span class='markdown_spoiler'>Secret</span>",
    gender = "Female",
    age = "1000+",
    bloodType = null,
    dateOfBirth = null,
    favourites = 61234,
    isFavourite = true,
    siteUrl = null,
    appearances = ContentPage(emptyList(), hasNextPage = false)
)

@ThemePreviews
@Composable
private fun CharacterContentPreview() {
    TsuzukiTheme {
        CharacterContent(
            state = PersonUiState.Content(previewCharacter, isFavourite = true),
            appearances = PagedItems(
                PreviewListEntries.all.map {
                    CharacterAppearance(it.media, "MAIN", PersonLite(112215, "Atsumi Tanezaki", null))
                },
                nextPage = 2
            ),
            events = emptyFlow(),
            onBack = {},
            onRetry = {},
            onToggleFavourite = {},
            onLoadMore = {},
            onOpenMedia = {},
            onLogIn = {}
        )
    }
}

@ThemePreviews
@Composable
private fun StaffContentPreview() {
    TsuzukiTheme {
        StaffContent(
            state = PersonUiState.Content(
                StaffDetail(
                    id = 112215,
                    name = PersonName("Atsumi Tanezaki", "Atsumi Tanezaki", "種﨑敦美", emptyList()),
                    imageUrl = null,
                    descriptionHtml = null,
                    occupations = listOf("Voice Actor"),
                    gender = "Female",
                    age = 38,
                    dateOfBirth = FuzzyDate(1988, 9, 27),
                    homeTown = "Oita, Japan",
                    yearsActive = listOf(2008),
                    favourites = 20000,
                    isFavourite = false,
                    siteUrl = null,
                    characters = ContentPage(emptyList(), false),
                    roles = ContentPage(emptyList(), false)
                ),
                isFavourite = false
            ),
            characters = PagedItems(
                listOf(VoicedCharacter(PersonLite(176754, "Frieren", null), "MAIN", PreviewListEntries.frieren.media)),
                nextPage = null
            ),
            roles = PagedItems(listOf(ProductionRole(PreviewListEntries.frieren.media, "Theme Song")), nextPage = null),
            events = emptyFlow(),
            onBack = {},
            onRetry = {},
            onToggleFavourite = {},
            onLoadMoreCharacters = {},
            onLoadMoreRoles = {},
            onOpenCharacter = {},
            onOpenMedia = {},
            onLogIn = {}
        )
    }
}

@ThemePreviews
@Composable
private fun PersonErrorPreview() {
    TsuzukiTheme {
        CharacterContent(
            state = PersonUiState.Error(AppError.Offline),
            appearances = PagedItems(emptyList(), null),
            events = emptyFlow(),
            onBack = {},
            onRetry = {},
            onToggleFavourite = {},
            onLoadMore = {},
            onOpenMedia = {},
            onLogIn = {}
        )
    }
}
