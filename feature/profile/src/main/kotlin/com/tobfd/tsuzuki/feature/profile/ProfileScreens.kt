package com.tobfd.tsuzuki.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.StatusDot
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBackTopBar
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiPullToRefresh
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.ActivityDay
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.Favourites
import com.tobfd.tsuzuki.core.model.FollowUser
import com.tobfd.tsuzuki.core.model.ListStatistics
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.PersonLite
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.model.UserProfile
import com.tobfd.tsuzuki.core.ui.ActivityCard
import com.tobfd.tsuzuki.core.ui.ActivityHeatmap
import com.tobfd.tsuzuki.core.ui.AniListHtmlText
import com.tobfd.tsuzuki.core.ui.MediaCoverCard
import com.tobfd.tsuzuki.core.ui.PersonCoverCard
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.ScrollToTopOnTabReselect
import com.tobfd.tsuzuki.core.ui.UserAvatar
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.message
import com.tobfd.tsuzuki.core.ui.statusColor
import java.time.LocalDate
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Root of the Profile tab: the viewer's own profile. */
@Serializable
data object ProfileRoute : NavKey

/** Another user's profile; [name] titles the page while it loads. */
@Serializable
data class UserRoute(val id: Int, val name: String) : NavKey

/** The profile tabs, in order. */
internal enum class ProfileTab(val labelRes: Int) {
    Overview(R.string.profile_tab_overview),
    Favourites(R.string.profile_tab_favourites),
    Stats(R.string.profile_tab_stats),
    Social(R.string.profile_tab_social)
}

/** Callbacks a profile needs to leave itself. */
data class ProfileNavigation(
    val onOpenMedia: (Int) -> Unit,
    val onOpenCharacter: (Int) -> Unit,
    val onOpenStaff: (Int) -> Unit,
    val onOpenUser: (id: Int, name: String) -> Unit,
    val onLogIn: () -> Unit
)

/**
 * Profile tab content (docs/DESIGN.md, Profile): the viewer's profile, or a log-in state for guests.
 * The app shell draws the top bar with bell and settings.
 */
@Composable
fun ProfileScreen(
    viewerId: Int?,
    navigation: ProfileNavigation,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    if (viewerId == null) {
        EmptyState(
            icon = painterResource(TsuzukiIcons.Person),
            title = stringResource(R.string.profile_guest_title),
            message = stringResource(R.string.profile_guest_message),
            actionLabel = stringResource(R.string.profile_log_in),
            onAction = navigation.onLogIn,
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    } else {
        ProfileRoot(userId = viewerId, navigation = navigation, modifier = modifier, contentPadding = contentPadding)
    }
}

/** The settings button the Profile tab puts into the shell's top bar, next to the bell. */
@Composable
fun ProfileSettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(TsuzukiIcons.Settings), contentDescription = stringResource(R.string.profile_settings))
    }
}

/** Another user's profile, pushed with its own back top bar. */
@Composable
fun UserScreen(
    userId: Int,
    userName: String,
    onBack: () -> Unit,
    navigation: ProfileNavigation,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TsuzukiBackTopBar(title = userName, onBack = onBack) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        ProfileRoot(
            userId = userId,
            navigation = navigation,
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                end = innerPadding.calculateEndPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.large
            )
        )
    }
}

@Composable
private fun ProfileRoot(
    userId: Int,
    navigation: ProfileNavigation,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val viewModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
        key = "profile-$userId",
        creationCallback = { it.create(userId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        events = viewModel.eventFlow,
        navigation = navigation,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        onToggleFollow = viewModel::onToggleFollow,
        onToggleLike = viewModel::onToggleLike,
        onShowFollowList = viewModel::onShowFollowList,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    events: Flow<ProfileEvent>,
    navigation: ProfileNavigation,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFollow: () -> Unit,
    onToggleLike: (Activity) -> Unit,
    onShowFollowList: (FollowList) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                ProfileEvent.LogInToUse -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.profile_log_in_to_use),
                        actionLabel = resources.getString(R.string.profile_log_in)
                    )
                    if (result == SnackbarResult.ActionPerformed) navigation.onLogIn()
                }

                is ProfileEvent.Failed -> failure = event.error
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

    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            ProfileUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is ProfileUiState.Error -> ErrorState(
                title = stringResource(R.string.profile_error_title),
                message = state.error.message(),
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center)
            )

            is ProfileUiState.Content -> TsuzukiPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                ProfileList(
                    state = state,
                    navigation = navigation,
                    contentPadding = contentPadding,
                    onToggleFollow = onToggleFollow,
                    onToggleLike = onToggleLike,
                    onShowFollowList = onShowFollowList
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding())
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileList(
    state: ProfileUiState.Content,
    navigation: ProfileNavigation,
    contentPadding: PaddingValues,
    onToggleFollow: () -> Unit,
    onToggleLike: (Activity) -> Unit,
    onShowFollowList: (FollowList) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val side = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection)
    )
    var tab by rememberSaveable { mutableStateOf(ProfileTab.Overview) }
    val listState = rememberLazyListState()
    ScrollToTopOnTabReselect(listState)
    LaunchedEffect(tab) { if (tab == ProfileTab.Social) onShowFollowList(state.followList) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
    ) {
        item(key = "header") {
            Header(state = state, side = side, onToggleFollow = onToggleFollow)
        }
        item(key = "stats") { StatsRow(state.profile.anime, modifier = Modifier.padding(side)) }
        stickyHeader(key = "tabs") {
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                ProfileTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        text = { Text(stringResource(entry.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        when (tab) {
            ProfileTab.Overview -> overview(state, side, navigation, onToggleLike)
            ProfileTab.Favourites -> favourites(state.profile.favourites, side, navigation)
            ProfileTab.Stats -> stats(state.profile, side)
            ProfileTab.Social -> social(state, side, navigation, onShowFollowList)
        }
    }
}

@Composable
private fun Header(state: ProfileUiState.Content, side: PaddingValues, onToggleFollow: () -> Unit) {
    val profile = state.profile
    val uriHandler = LocalUriHandler.current
    Column {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TsuzukiSizes.profileBanner)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                profile.bannerUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            UserAvatar(
                avatarUrl = profile.avatarUrl,
                name = profile.name,
                size = TsuzukiSizes.profileAvatar,
                modifier = Modifier
                    .padding(side)
                    .padding(top = TsuzukiSizes.profileBanner - TsuzukiSizes.profileAvatar / 2)
                    .border(TsuzukiSpacing.extraSmall, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        Row(
            modifier = Modifier
                .padding(side)
                .padding(top = TsuzukiSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.isFollower && !state.isOwn) {
                    Text(
                        text = stringResource(R.string.profile_follows_you),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (!state.isOwn) {
                if (state.isFollowing) {
                    FilledTonalButton(onClick = onToggleFollow) { Text(stringResource(R.string.profile_following)) }
                } else {
                    Button(onClick = onToggleFollow) {
                        Icon(painterResource(TsuzukiIcons.PersonAdd), contentDescription = null)
                        Spacer(Modifier.size(TsuzukiSpacing.small))
                        Text(stringResource(R.string.profile_follow))
                    }
                }
            }
            profile.siteUrl?.let { url ->
                IconButton(onClick = { uriHandler.openUri(url) }) {
                    Icon(
                        painterResource(TsuzukiIcons.OpenInNew),
                        contentDescription = stringResource(R.string.profile_open_on_anilist)
                    )
                }
            }
        }
    }
}

/** Total anime, episodes watched, days watched and mean score. */
@Composable
private fun StatsRow(anime: ListStatistics, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = modifier.fillMaxWidth()) {
        listOf(
            stringResource(R.string.profile_total_anime) to "%,d".format(locale, anime.count),
            stringResource(R.string.profile_episodes_watched) to "%,d".format(locale, anime.progress),
            stringResource(R.string.profile_days_watched) to "%.1f".format(locale, anime.daysWatched),
            stringResource(R.string.profile_mean_score) to meanScore(anime.meanScore, locale)
        ).forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun meanScore(score: Double, locale: Locale): String = if (score > 0) "%.1f".format(locale, score) else "–"

private fun LazyListScope.sectionTitle(key: String, text: @Composable () -> String, side: PaddingValues) {
    item(key = key) {
        Text(
            text = text(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(side)
                .padding(top = TsuzukiSpacing.small)
                .semantics { heading() }
        )
    }
}

private fun LazyListScope.overview(
    state: ProfileUiState.Content,
    side: PaddingValues,
    navigation: ProfileNavigation,
    onToggleLike: (Activity) -> Unit
) {
    val profile = state.profile
    profile.aboutHtml?.let { html ->
        item(key = "about") { AniListHtmlText(html, modifier = Modifier.padding(side)) }
    }
    sectionTitle("historyTitle", { stringResource(R.string.profile_activity_history) }, side)
    item(key = "history") {
        ActivityHeatmap(days = profile.activityHistory, today = state.today, modifier = Modifier.padding(side))
    }
    sectionTitle("recentTitle", { stringResource(R.string.profile_recent_activity) }, side)
    if (profile.recentActivity.isEmpty()) {
        item(key = "noActivity") { MutedText(stringResource(R.string.profile_no_activity), Modifier.padding(side)) }
    }
    items(profile.recentActivity, key = { "activity-${it.id}" }) { activity ->
        ActivityCard(
            activity = activity,
            onLikeClick = { onToggleLike(activity) },
            onUserClick = { navigation.onOpenUser(activity.user.id, activity.user.name) },
            onMediaClick = navigation.onOpenMedia,
            modifier = Modifier.padding(side)
        )
    }
}

private fun LazyListScope.favourites(favourites: Favourites, side: PaddingValues, navigation: ProfileNavigation) {
    if (favourites.isEmpty) {
        item(key = "noFavourites") {
            EmptyState(
                icon = painterResource(TsuzukiIcons.Favorite),
                title = stringResource(R.string.profile_no_favourites_title),
                message = stringResource(R.string.profile_no_favourites_message),
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    listOf(
        R.string.profile_favourites_anime to favourites.anime,
        R.string.profile_favourites_manga to favourites.manga
    ).filter { it.second.isNotEmpty() }.forEach { (title, media) ->
        sectionTitle("fav-$title", { stringResource(title) }, side)
        item(key = "favRow-$title") {
            LazyRow(contentPadding = side, horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)) {
                items(media, key = { it.id }) { item ->
                    MediaCoverCard(media = item, onClick = { navigation.onOpenMedia(item.id) })
                }
            }
        }
    }
    listOf(
        Triple(R.string.profile_favourites_characters, favourites.characters, navigation.onOpenCharacter),
        Triple(R.string.profile_favourites_staff, favourites.staff, navigation.onOpenStaff)
    ).filter { it.second.isNotEmpty() }.forEach { (title, people, open) ->
        sectionTitle("fav-$title", { stringResource(title) }, side)
        item(key = "favRow-$title") {
            LazyRow(contentPadding = side, horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)) {
                items(people, key = {
                    it.id
                }) { person -> PersonCoverCard(person = person, onClick = { open(person.id) }) }
            }
        }
    }
}

private fun LazyListScope.stats(profile: UserProfile, side: PaddingValues) {
    listOf(MediaType.ANIME to profile.anime, MediaType.MANGA to profile.manga).forEach { (type, statistics) ->
        val anime = type == MediaType.ANIME
        sectionTitle(
            "statsTitle-$type",
            { stringResource(if (anime) R.string.profile_stats_anime else R.string.profile_stats_manga) },
            side
        )
        item(key = "statsNumbers-$type") {
            val locale = LocalConfiguration.current.locales[0]
            val numbers = buildList {
                add(stringResource(R.string.profile_stats_count) to "%,d".format(locale, statistics.count))
                if (anime) {
                    add(stringResource(R.string.profile_stats_episodes) to "%,d".format(locale, statistics.progress))
                    add(stringResource(R.string.profile_stats_days) to "%.1f".format(locale, statistics.daysWatched))
                } else {
                    add(stringResource(R.string.profile_stats_chapters) to "%,d".format(locale, statistics.progress))
                    add(stringResource(R.string.profile_stats_volumes) to "%,d".format(locale, statistics.volumes))
                }
                add(stringResource(R.string.profile_stats_mean) to meanScore(statistics.meanScore, locale))
                add(stringResource(R.string.profile_stats_deviation) to meanScore(statistics.standardDeviation, locale))
            }
            NumberGrid(numbers, modifier = Modifier.padding(side))
        }
        item(key = "statsStatus-$type") {
            StatusBars(
                title = stringResource(
                    if (anime) R.string.profile_anime_by_status else R.string.profile_manga_by_status
                ),
                type = type,
                statuses = statistics.statuses,
                modifier = Modifier.padding(side)
            )
        }
    }
}

@Composable
private fun NumberGrid(numbers: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        numbers.chunked(3).forEach { row ->
            Row {
                row.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(text = value, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** One bar per list status in its status color, longest for the biggest count. */
@Composable
private fun StatusBars(
    title: String,
    type: MediaType,
    statuses: Map<MediaListStatus, Int>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        val max = statuses.values.maxOrNull()?.takeIf { it > 0 }
        if (max == null) {
            MutedText(stringResource(R.string.profile_no_stats))
            return@Column
        }
        MediaListStatus.entries.filter { (statuses[it] ?: 0) > 0 }.forEach { status ->
            val count = statuses.getValue(status)
            val color = status.statusColor()
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
            ) {
                StatusDot(color)
                Text(
                    text = stringResource(status.labelRes(type)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.4f)
                )
                Box(modifier = Modifier.weight(0.45f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(count / max.toFloat())
                            .height(TsuzukiSizes.statusBar)
                            .background(color.color, MaterialTheme.shapes.extraSmall)
                    )
                }
                Text(
                    text = "%,d".format(LocalConfiguration.current.locales[0], count),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.15f)
                )
            }
        }
    }
}

private fun LazyListScope.social(
    state: ProfileUiState.Content,
    side: PaddingValues,
    navigation: ProfileNavigation,
    onShowFollowList: (FollowList) -> Unit
) {
    item(key = "socialToggle") {
        SegmentedToggle(
            options = persistentListOf(
                stringResource(R.string.profile_social_following),
                stringResource(R.string.profile_social_followers)
            ),
            selectedIndex = state.followList.ordinal,
            onSelect = { onShowFollowList(FollowList.entries[it]) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(side)
        )
    }
    when (val list = state.followLists[state.followList]) {
        null, FollowListState.Loading -> item(key = "socialLoading") {
            Box(Modifier.fillMaxWidth().padding(TsuzukiSpacing.extraLarge), Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is FollowListState.Error -> item(key = "socialError") {
            ErrorState(
                title = stringResource(R.string.profile_error_title),
                message = list.error.message(),
                onRetry = { onShowFollowList(state.followList) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        is FollowListState.Loaded -> {
            if (list.page.items.isEmpty()) {
                item(key = "socialEmpty") {
                    MutedText(
                        stringResource(
                            if (state.followList == FollowList.Following) {
                                R.string.profile_social_empty_following
                            } else {
                                R.string.profile_social_empty_followers
                            }
                        ),
                        Modifier.padding(side)
                    )
                }
            }
            items(list.page.items, key = { "user-${state.followList}-${it.user.id}" }) { follow ->
                FollowRow(follow, onClick = {
                    navigation.onOpenUser(follow.user.id, follow.user.name)
                }, Modifier.padding(side))
            }
            if (list.page.hasNextPage) {
                state.profile.siteUrl?.let { url ->
                    item(key = "socialMore") {
                        val uriHandler = LocalUriHandler.current
                        TextButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.padding(side)) {
                            Text(stringResource(R.string.profile_social_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRow(follow: FollowUser, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(TsuzukiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
        ) {
            UserAvatar(avatarUrl = follow.user.avatarUrl, name = follow.user.name, size = TsuzukiSizes.minTouchTarget)
            Text(
                text = follow.user.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (follow.followsViewer) {
                Text(
                    text = stringResource(R.string.profile_follows_you),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private val previewProfile = UserProfile(
    id = 5,
    name = "tobfd",
    aboutHtml = "Watching <b>Frieren</b> again.",
    avatarUrl = null,
    bannerUrl = null,
    siteUrl = "https://anilist.co/user/tobfd",
    isFollowing = false,
    isFollower = true,
    anime = ListStatistics(
        120,
        2_880,
        0,
        69_120,
        78.5,
        10.2,
        mapOf(MediaListStatus.COMPLETED to 100, MediaListStatus.CURRENT to 12, MediaListStatus.PLANNING to 40)
    ),
    manga = ListStatistics.Empty,
    activityHistory = (0 until 84 step 3).map { ActivityDay(LocalDate.of(2026, 9, 30).minusDays(it.toLong()), it % 7) },
    favourites = Favourites(
        anime = PreviewListEntries.all.map { it.media },
        manga = emptyList(),
        characters = listOf(PersonLite(176754, "Frieren", null)),
        staff = emptyList()
    ),
    recentActivity = emptyList()
)

private val previewNavigation = ProfileNavigation({}, {}, {}, { _, _ -> }, {})

@ThemePreviews
@Composable
private fun ProfileContentPreview() {
    TsuzukiTheme {
        ProfileContent(
            state = ProfileUiState.Content(
                profile = previewProfile,
                isOwn = false,
                isFollowing = false,
                isRefreshing = false,
                followList = FollowList.Following,
                followLists = mapOf(
                    FollowList.Following to FollowListState.Loaded(
                        ContentPage(listOf(FollowUser(UserLite(9, "GeckoTV", null), true)), false)
                    )
                ),
                today = LocalDate.of(2026, 9, 30)
            ),
            events = emptyFlow(),
            navigation = previewNavigation,
            onRetry = {},
            onRefresh = {},
            onToggleFollow = {},
            onToggleLike = {},
            onShowFollowList = {}
        )
    }
}

@ThemePreviews
@Composable
private fun ProfileGuestPreview() {
    TsuzukiTheme {
        ProfileScreen(viewerId = null, navigation = previewNavigation)
    }
}
