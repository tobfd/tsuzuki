package com.tobfd.tsuzuki.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.tobfd.tsuzuki.R
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.component.ErrorState
import com.tobfd.tsuzuki.core.designsystem.component.InitialAvatar
import com.tobfd.tsuzuki.core.designsystem.component.PlusOneButton
import com.tobfd.tsuzuki.core.designsystem.component.SectionHeader
import com.tobfd.tsuzuki.core.designsystem.component.SegmentedToggle
import com.tobfd.tsuzuki.core.designsystem.component.StatusChip
import com.tobfd.tsuzuki.core.designsystem.component.StatusDot
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiBanner
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiLogo
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiTopBar
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.ui.MediaCover
import com.tobfd.tsuzuki.core.ui.coverColorOrNull
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.score.ScoreText
import com.tobfd.tsuzuki.core.ui.statusColor
import kotlinx.collections.immutable.persistentListOf

// Sample data from AniList (Frieren, id 154587), as used across docs/DESIGN.md.
private const val FRIEREN_TITLE = "Sousou no Frieren"
private const val FRIEREN_COVER =
    "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/" + "bx154587-qQTzQnEJJ3oB.jpg"
private const val FRIEREN_COLOR = "#bbf1a1"
private const val FRIEREN_EPISODES = 28
private const val SAMPLE_PROGRESS = 18
private const val SAMPLE_VIEWER = "tobfd"

private val SwatchWidth = 104.dp
private val SwatchHeight = 56.dp
private val ShapeSample = 56.dp

@Composable
fun CatalogScreen(
    colorSource: ColorSource,
    darkTheme: Boolean,
    onColorSourceChange: (ColorSource) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            // Clip below the status bar so scrolled content never runs under it.
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                end = innerPadding.calculateEndPadding(layoutDirection) + TsuzukiSpacing.screenMargin,
                top = TsuzukiSpacing.large,
                bottom = innerPadding.calculateBottomPadding() + TsuzukiSpacing.large
            ),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.sectionGap)
        ) {
            item {
                ThemeControls(colorSource, darkTheme, onColorSourceChange, onDarkThemeChange)
            }
            item { CatalogSection(R.string.catalog_section_colors) { ColorRoles() } }
            item { CatalogSection(R.string.catalog_section_status_colors) { StatusColorSwatches() } }
            item { CatalogSection(R.string.catalog_section_typography) { TypeScale() } }
            item { CatalogSection(R.string.catalog_section_shapes) { ShapeSamples() } }
            item { CatalogSection(R.string.catalog_section_plus_one) { PlusOneSamples() } }
            item { CatalogSection(R.string.catalog_section_status_chip) { StatusChipSamples() } }
            item { CatalogSection(R.string.catalog_section_score_text) { ScoreSamples() } }
            item { CatalogSection(R.string.catalog_section_section_header) { SectionHeaderSamples() } }
            item { CatalogSection(R.string.catalog_section_media_cover) { MediaCoverSamples() } }
            item { CatalogSection(R.string.catalog_section_segmented_toggle) { SegmentedToggleSamples() } }
            item { CatalogSection(R.string.catalog_section_top_bar) { TopBarSamples() } }
            item {
                CatalogSection(R.string.catalog_section_logo) {
                    TsuzukiLogo()
                }
            }
            item {
                CatalogSection(R.string.catalog_section_banner) {
                    TsuzukiBanner(
                        message = stringResource(R.string.catalog_banner_message),
                        actionLabel = stringResource(R.string.catalog_banner_action),
                        onAction = {}
                    )
                    TsuzukiBanner(message = stringResource(R.string.catalog_banner_message_short))
                }
            }
            item {
                CatalogSection(R.string.catalog_section_empty_state) {
                    EmptyState(
                        icon = painterResource(TsuzukiIcons.Inbox),
                        title = stringResource(R.string.catalog_empty_title),
                        message = stringResource(R.string.catalog_empty_message),
                        actionLabel = stringResource(R.string.catalog_empty_action),
                        onAction = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                CatalogSection(R.string.catalog_section_error_state) {
                    ErrorState(
                        title = stringResource(R.string.catalog_error_title),
                        message = stringResource(R.string.catalog_error_message),
                        onRetry = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeControls(
    colorSource: ColorSource,
    darkTheme: Boolean,
    onColorSourceChange: (ColorSource) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)) {
        Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineMedium)
        SegmentedToggle(
            options = persistentListOf(
                stringResource(R.string.catalog_color_material_you),
                stringResource(R.string.catalog_color_anilist_blue)
            ),
            selectedIndex = colorSource.ordinal,
            onSelect = { onColorSourceChange(ColorSource.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        SegmentedToggle(
            options = persistentListOf(
                stringResource(R.string.catalog_mode_light),
                stringResource(R.string.catalog_mode_dark)
            ),
            selectedIndex = if (darkTheme) 1 else 0,
            onSelect = { onDarkThemeChange(it == 1) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CatalogSection(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

// Role names are Compose ColorScheme identifiers, shown as-is in this developer tool.
@Composable
private fun ColorRoles() {
    val c = MaterialTheme.colorScheme
    val roles = listOf(
        Triple("primary", c.primary, c.onPrimary),
        Triple("primaryContainer", c.primaryContainer, c.onPrimaryContainer),
        Triple("secondary", c.secondary, c.onSecondary),
        Triple("secondaryContainer", c.secondaryContainer, c.onSecondaryContainer),
        Triple("tertiary", c.tertiary, c.onTertiary),
        Triple("tertiaryContainer", c.tertiaryContainer, c.onTertiaryContainer),
        Triple("error", c.error, c.onError),
        Triple("errorContainer", c.errorContainer, c.onErrorContainer),
        Triple("surface", c.surface, c.onSurface),
        Triple("surfaceContainerLow", c.surfaceContainerLow, c.onSurface),
        Triple("surfaceContainer", c.surfaceContainer, c.onSurface),
        Triple("surfaceContainerHigh", c.surfaceContainerHigh, c.onSurface),
        Triple("surfaceContainerHighest", c.surfaceContainerHighest, c.onSurface),
        Triple("inverseSurface", c.inverseSurface, c.inverseOnSurface),
        Triple("outline", c.outline, c.surface),
        Triple("outlineVariant", c.outlineVariant, c.onSurface)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
    ) {
        roles.forEach { (name, color, onColor) -> Swatch(name, color, onColor) }
    }
}

@Composable
private fun Swatch(name: String, color: Color, onColor: Color) {
    Surface(
        modifier = Modifier.size(SwatchWidth, SwatchHeight),
        shape = MaterialTheme.shapes.small,
        color = color,
        contentColor = onColor
    ) {
        Box(modifier = Modifier.padding(TsuzukiSpacing.small)) {
            Text(name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatusColorSwatches() {
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        MediaListStatus.entries.forEach { status ->
            val colors = status.statusColor()
            Row(
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(colors)
                Text(
                    text = stringResource(status.labelRes(MediaType.ANIME)),
                    color = colors.color,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(SwatchWidth)
                )
                Swatch("container", colors.container, colors.onContainer)
            }
        }
    }
}

// Style names are Compose Typography identifiers, shown as-is in this developer tool.
@Composable
private fun TypeScale() {
    val t = MaterialTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "displaySmall" to t.displaySmall,
        "headlineMedium" to t.headlineMedium,
        "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge,
        "titleMedium" to t.titleMedium,
        "bodyLarge" to t.bodyLarge,
        "bodyMedium" to t.bodyMedium,
        "labelLarge" to t.labelLarge,
        "labelMedium" to t.labelMedium,
        "labelSmall" to t.labelSmall
    )
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
        styles.forEach { (name, style) ->
            Text(text = "$name · $FRIEREN_TITLE", style = style)
        }
    }
}

@Composable
private fun ShapeSamples() {
    val s = MaterialTheme.shapes
    val shapes: List<Pair<String, Shape>> = listOf(
        "xs" to s.extraSmall,
        "s" to s.small,
        "m" to s.medium,
        "l" to s.large,
        "xl" to s.extraLarge,
        "full" to CircleShape
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        shapes.forEach { (name, shape) ->
            Box(
                modifier = Modifier
                    .size(ShapeSample)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
                contentAlignment = Alignment.Center
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PlusOneSamples() {
    var progress by rememberSaveable { mutableIntStateOf(SAMPLE_PROGRESS) }
    val increment = { progress = if (progress >= FRIEREN_EPISODES) 0 else progress + 1 }
    val completes = progress + 1 == FRIEREN_EPISODES
    Text(
        text = stringResource(R.string.catalog_progress, progress, FRIEREN_EPISODES),
        style = MaterialTheme.typography.titleMedium
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraLarge),
        verticalAlignment = Alignment.Bottom
    ) {
        // Both enabled variants count up; only the disabled one ignores taps.
        LabeledVariant(R.string.catalog_plus_one_standard) {
            PlusOneButton(onClick = increment, completesEntry = completes)
        }
        LabeledVariant(R.string.catalog_plus_one_dense) {
            PlusOneButton(onClick = increment, dense = true, completesEntry = completes)
        }
        LabeledVariant(R.string.catalog_plus_one_disabled) {
            PlusOneButton(onClick = {}, enabled = false)
        }
    }
}

/** A component sample with its variant name underneath. */
@Composable
private fun LabeledVariant(label: Int, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
    ) {
        content()
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusChipSamples() {
    var selected by remember { mutableStateOf(MediaListStatus.CURRENT) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        MediaListStatus.entries.forEach { status ->
            StatusChip(
                label = stringResource(status.labelRes(MediaType.ANIME)),
                statusColor = status.statusColor(),
                selected = status == selected,
                onClick = { selected = status }
            )
        }
    }
}

@Composable
private fun ScoreSamples() {
    val samples = listOf(
        ScoreFormat.POINT_100 to 85.0,
        ScoreFormat.POINT_10_DECIMAL to 8.5,
        ScoreFormat.POINT_10 to 8.0,
        ScoreFormat.POINT_5 to 4.0,
        ScoreFormat.POINT_3 to 3.0,
        ScoreFormat.POINT_3 to 2.0,
        ScoreFormat.POINT_3 to 1.0,
        ScoreFormat.POINT_10_DECIMAL to 0.0
    )
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
        samples.forEach { (format, score) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.catalog_score_format, format.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(SwatchWidth * 2)
                )
                ScoreText(score = score, format = format, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SectionHeaderSamples() {
    SectionHeader(title = stringResource(R.string.catalog_header_in_progress), onSeeAllClick = {})
    SectionHeader(title = stringResource(R.string.catalog_header_planning))
}

@Composable
private fun MediaCoverSamples() {
    val placeholder = coverColorOrNull(FRIEREN_COLOR)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap),
        verticalAlignment = Alignment.Bottom
    ) {
        MediaCover(
            imageUrl = FRIEREN_COVER,
            contentDescription = FRIEREN_TITLE,
            placeholderColor = placeholder,
            badge = stringResource(R.string.catalog_cover_badge, SAMPLE_PROGRESS, FRIEREN_EPISODES),
            progress = SAMPLE_PROGRESS.toFloat() / FRIEREN_EPISODES,
            modifier = Modifier.width(TsuzukiSizes.inProgressCover.width)
        )
        MediaCover(
            imageUrl = FRIEREN_COVER,
            contentDescription = FRIEREN_TITLE,
            placeholderColor = placeholder,
            modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
        )
        MediaCover(
            imageUrl = null,
            contentDescription = stringResource(R.string.catalog_cover_no_image),
            placeholderColor = placeholder,
            modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
        )
        MediaCover(
            imageUrl = null,
            contentDescription = stringResource(R.string.catalog_cover_no_image),
            modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
        )
    }
}

@Composable
private fun SegmentedToggleSamples() {
    var type by rememberSaveable { mutableIntStateOf(0) }
    var feed by rememberSaveable { mutableIntStateOf(0) }
    SegmentedToggle(
        options = persistentListOf(stringResource(R.string.catalog_anime), stringResource(R.string.catalog_manga)),
        selectedIndex = type,
        onSelect = { type = it },
        modifier = Modifier.fillMaxWidth()
    )
    SegmentedToggle(
        options = persistentListOf(stringResource(R.string.catalog_following), stringResource(R.string.catalog_global)),
        selectedIndex = feed,
        onSelect = { feed = it }
    )
}

@Composable
private fun TopBarSamples() {
    listOf(
        R.string.catalog_top_bar_home to 3,
        R.string.catalog_top_bar_lists to 0,
        R.string.catalog_top_bar_home to 120
    ).forEach { (title, unread) ->
        TsuzukiTopBar(
            title = stringResource(title),
            unreadNotificationCount = unread,
            onNotificationsClick = {},
            onAvatarClick = {},
            avatar = { InitialAvatar(name = SAMPLE_VIEWER) },
            windowInsets = WindowInsets(0)
        )
    }
}
