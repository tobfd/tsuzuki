package com.tobfd.tsuzuki.feature.media

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.designsystem.R as DesignR
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaDetail
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.headline
import com.tobfd.tsuzuki.core.ui.MediaCover
import com.tobfd.tsuzuki.core.ui.coverColorOrNull
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.message
import com.tobfd.tsuzuki.core.ui.progressText
import com.tobfd.tsuzuki.core.ui.statusColor
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Detail page of an anime or manga. */
@Serializable
data class MediaRoute(val id: Int) : NavKey

private val BannerHeight = 200.dp
private val HeaderCoverWidth = 112.dp
private val CoverOverlap = 64.dp

/** The anchored tabs, in page order. */
internal enum class DetailSection(val labelRes: Int) {
    Overview(R.string.media_tab_overview),
    Characters(R.string.media_tab_characters),
    Stats(R.string.media_tab_stats),
    Social(R.string.media_tab_social),
    Recommendations(R.string.media_tab_recommendations)
}

/** Items before the first section: the header and the tab row. */
private const val SECTION_OFFSET = 2

/**
 * The detail page (docs/DESIGN.md, Media detail). [onEditEntry] opens the list editor sheet;
 * [onLogIn] leaves guest mode for the login screen.
 */
@Composable
fun MediaScreen(
    mediaId: Int,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenStaff: (Int) -> Unit,
    onEditEntry: (Int) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaDetailContent(
        viewModel = hiltViewModel<MediaDetailViewModel, MediaDetailViewModel.Factory>(
            key = "media-$mediaId",
            creationCallback = { it.create(mediaId) }
        ),
        onBack = onBack,
        onOpenMedia = onOpenMedia,
        onOpenCharacter = onOpenCharacter,
        onOpenStaff = onOpenStaff,
        onEditEntry = onEditEntry,
        onLogIn = onLogIn,
        modifier = modifier
    )
}

@Composable
internal fun MediaDetailContent(
    viewModel: MediaDetailViewModel,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenStaff: (Int) -> Unit,
    onEditEntry: (Int) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var failure by remember { mutableStateOf<AppError?>(null) }
    var sharing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is MediaDetailEvent.OpenEditor -> onEditEntry(event.mediaId)

                MediaDetailEvent.LogInToUse -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.media_log_in_to_use),
                        actionLabel = resources.getString(R.string.media_log_in)
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onLogIn()
                }

                is MediaDetailEvent.Failed -> failure = event.error
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

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when (val current = state) {
            MediaDetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is MediaDetailUiState.Error -> ErrorState(
                title = stringResource(R.string.media_error_title),
                message = current.error.message(),
                onRetry = viewModel::onRetry,
                modifier = Modifier.align(Alignment.Center)
            )

            is MediaDetailUiState.Content -> DetailPage(
                state = current,
                onBack = onBack,
                onListButton = viewModel::onListButton,
                onToggleFavourite = viewModel::onToggleFavourite,
                onShare = { sharing = true },
                onOpenMedia = onOpenMedia,
                onOpenCharacter = onOpenCharacter,
                onOpenStaff = onOpenStaff
            )
        }
        if (state !is MediaDetailUiState.Content) FloatingButtons(onBack = onBack, onShare = null)
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    val content = state as? MediaDetailUiState.Content
    if (sharing && content != null) {
        ShareFlow(state = content, onDone = { sharing = false })
    }
}

/** Back and share float over the banner in surfaceContainerHigh circles. */
@Composable
private fun FloatingButtons(onBack: () -> Unit, onShare: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = TsuzukiSpacing.small, vertical = TsuzukiSpacing.extraSmall)
    ) {
        CircleButton(TsuzukiIcons.ArrowBack, stringResource(DesignR.string.designsystem_back), onBack)
        Spacer(Modifier.weight(1f))
        if (onShare != null) CircleButton(TsuzukiIcons.Share, stringResource(R.string.media_share), onShare)
    }
}

@Composable
private fun CircleButton(icon: Int, description: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        IconButton(onClick = onClick) { Icon(painterResource(icon), contentDescription = description) }
    }
}

@Composable
private fun DetailPage(
    state: MediaDetailUiState.Content,
    onBack: () -> Unit,
    onListButton: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenStaff: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selected by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex - SECTION_OFFSET).coerceIn(0, DetailSection.entries.lastIndex)
        }
    }
    val headerVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Box {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Header(
                    state = state,
                    onListButton = onListButton,
                    onToggleFavourite = onToggleFavourite,
                    onShare = onShare
                )
            }
            // Once the header has scrolled away, back and share move into the pinned tab bar.
            stickyHeader(key = "tabs") {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(modifier = Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                        if (!headerVisible) {
                            IconButton(onClick = onBack) {
                                val backLabel = stringResource(DesignR.string.designsystem_back)
                                Icon(painterResource(TsuzukiIcons.ArrowBack), contentDescription = backLabel)
                            }
                        }
                        PrimaryScrollableTabRow(
                            selectedTabIndex = selected,
                            edgePadding = if (headerVisible) TsuzukiSpacing.screenMargin else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            DetailSection.entries.forEachIndexed { index, section ->
                                Tab(
                                    selected = index == selected,
                                    onClick = {
                                        scope.launch { listState.animateScrollToItem(index + SECTION_OFFSET) }
                                    },
                                    text = { Text(stringResource(section.labelRes)) }
                                )
                            }
                        }
                        if (!headerVisible) {
                            IconButton(onClick = onShare) {
                                val shareLabel = stringResource(R.string.media_share)
                                Icon(painterResource(TsuzukiIcons.Share), contentDescription = shareLabel)
                            }
                        }
                    }
                }
            }
            item(key = "overview") {
                OverviewSection(detail = state.detail, onOpenMedia = onOpenMedia)
            }
            item(key = "characters") {
                CharactersSection(
                    detail = state.detail,
                    onOpenCharacter = onOpenCharacter,
                    onOpenStaff = onOpenStaff
                )
            }
            item(key = "stats") {
                StatsSection(detail = state.detail)
            }
            item(key = "social") {
                SocialSection(state = state)
            }
            item(key = "recommendations") {
                RecommendationsSection(detail = state.detail, onOpenMedia = onOpenMedia)
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(TsuzukiSpacing.extraLarge)) }
        }
        if (headerVisible) FloatingButtons(onBack = onBack, onShare = onShare)
    }
}

@Composable
private fun Header(
    state: MediaDetailUiState.Content,
    onListButton: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit
) {
    val detail = state.detail
    val media = detail.media
    val scrim = MaterialTheme.colorScheme.surface
    Column {
        // The cover and score overlap the banner's lower edge.
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BannerHeight)
                    .background(coverColorOrNull(media.coverColor) ?: MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                val banner = detail.bannerUrl ?: detail.coverUrl
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to scrim))
                )
            }
            Row(
                modifier = Modifier.padding(
                    start = TsuzukiSpacing.screenMargin,
                    end = TsuzukiSpacing.screenMargin,
                    top = BannerHeight - CoverOverlap
                ),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large),
                verticalAlignment = Alignment.Bottom
            ) {
                MediaCover(
                    imageUrl = detail.coverUrl,
                    contentDescription = null,
                    placeholderColor = coverColorOrNull(media.coverColor),
                    modifier = Modifier.width(HeaderCoverWidth)
                )
                Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
                    Text(
                        text = headerMeta(detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    media.averageScore?.let {
                        Text(
                            text = stringResource(R.string.media_average_score, it),
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                    detail.rankings.headline()?.let { ranking ->
                        Text(
                            text = rankingText(ranking),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(
                start = TsuzukiSpacing.screenMargin,
                end = TsuzukiSpacing.screenMargin,
                top = TsuzukiSpacing.large,
                bottom = TsuzukiSpacing.medium
            ),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
        ) {
            Text(text = media.title.userPreferred, style = MaterialTheme.typography.headlineMedium)
            val subtitle = listOfNotNull(media.title.romaji, media.title.native)
                .filter { it != media.title.userPreferred }
                .joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ActionsRow(
                state = state,
                onListButton = onListButton,
                onToggleFavourite = onToggleFavourite,
                onShare = onShare
            )
        }
    }
}

@Composable
private fun ActionsRow(
    state: MediaDetailUiState.Content,
    onListButton: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit
) {
    val entry = state.entry
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        val statusColor = entry?.status?.statusColor()
        Button(
            onClick = onListButton,
            enabled = !state.adding,
            colors = statusColor
                ?.let { ButtonDefaults.buttonColors(containerColor = it.container, contentColor = it.onContainer) }
                ?: ButtonDefaults.buttonColors(),
            modifier = Modifier.weight(1f)
        ) {
            Text(text = listButtonText(entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val favourite = state.isFavourite
        val favouriteLabel = stringResource(if (favourite) R.string.media_unfavourite else R.string.media_favourite)
        val favouriteIcon = if (favourite) TsuzukiIcons.FavoriteFilled else TsuzukiIcons.Favorite
        val scheme = MaterialTheme.colorScheme
        val favouriteTint = if (favourite) scheme.error else scheme.onSurfaceVariant
        IconToggleButton(checked = favourite, onCheckedChange = { onToggleFavourite() }) {
            Icon(painter = painterResource(favouriteIcon), contentDescription = favouriteLabel, tint = favouriteTint)
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                val moreLabel = stringResource(R.string.media_more)
                Icon(painterResource(TsuzukiIcons.OpenInNew), contentDescription = moreLabel)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.detail.siteUrl?.let { url ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_open_on_anilist)) },
                        onClick = {
                            menuOpen = false
                            uriHandler.openUri(url)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.media_share)) },
                    onClick = {
                        menuOpen = false
                        onShare()
                    }
                )
            }
        }
    }
}

/** "Watching · 18 / 28", or "Add to list". */
@Composable
private fun listButtonText(entry: MediaListEntry?): String {
    if (entry == null) return stringResource(R.string.media_add_to_list)
    val status = stringResource(entry.status.labelRes(entry.type))
    return "$status · ${progressText(entry.progress, entry.media.total)}"
}

/** "TV · 28 episodes · Finished". */
@Composable
private fun headerMeta(detail: MediaDetail): String {
    val media = detail.media
    val total = media.total
    return listOfNotNull(
        media.format?.let { stringResource(it.labelRes()) },
        total?.let {
            if (media.type == MediaType.ANIME) {
                androidx.compose.ui.res.pluralStringResource(R.plurals.media_episodes, it, it)
            } else {
                androidx.compose.ui.res.pluralStringResource(R.plurals.media_chapters, it, it)
            }
        },
        media.status?.let { stringResource(it.labelRes()) }
    ).joinToString(" · ")
}

/** Shares the page's link as text (guests, or media not on the list). */
internal fun shareLinkIntent(title: String, url: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    },
    null
)

@Composable
internal fun rememberShareLink(): (String, String) -> Unit {
    val context = LocalContext.current
    return { title, url -> context.startActivity(shareLinkIntent(title, url)) }
}
