package com.tobfd.tsuzuki.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tobfd.tsuzuki.core.designsystem.R as DesignR
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.BrowseFilter
import com.tobfd.tsuzuki.core.model.BrowseSort
import com.tobfd.tsuzuki.core.model.MediaSeason
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.formats
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.message
import java.time.LocalDate
import kotlinx.coroutines.launch

/** The oldest year the stepper goes to; AniList has hardly anything earlier. */
private const val FIRST_YEAR = 1940

/** Announced seasons reach this far ahead. */
private const val YEARS_AHEAD = 2

/** Tags listed while typing in the tag search. */
private const val TAG_MATCHES = 24

/**
 * The filter sheet (docs/DESIGN.md, Browse): chip groups for format, release status, season, genres,
 * tags and sort, a year stepper, and "Reset" / "Show results". Changes stay a draft until "Show
 * results"; closing the sheet drops them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BrowseFilterSheet(
    draft: BrowseFilter,
    type: MediaType,
    today: LocalDate,
    options: FilterOptionsState,
    onDraftChange: (BrowseFilter) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onRetryOptions: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TsuzukiSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)
        ) {
            Text(
                text = stringResource(R.string.browse_filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            ChipGroup(stringResource(R.string.browse_filter_format)) {
                type.formats().forEach { format ->
                    ToggleChip(stringResource(format.labelRes()), selected = format in draft.formats) {
                        onDraftChange(draft.copy(formats = draft.formats.toggle(format)))
                    }
                }
            }
            ChipGroup(stringResource(R.string.browse_filter_status)) {
                MediaStatus.entries.forEach { status ->
                    ToggleChip(stringResource(status.labelRes()), selected = status in draft.statuses) {
                        onDraftChange(draft.copy(statuses = draft.statuses.toggle(status)))
                    }
                }
            }
            if (type == MediaType.ANIME) {
                ChipGroup(stringResource(R.string.browse_filter_season)) {
                    MediaSeason.entries.forEach { season ->
                        ToggleChip(stringResource(season.labelRes()), selected = draft.season == season) {
                            onDraftChange(draft.copy(season = season.takeIf { draft.season != it }))
                        }
                    }
                }
            }
            YearStepper(
                year = draft.year,
                currentYear = today.year,
                onYearChange = { onDraftChange(draft.copy(year = it)) }
            )
            when (options) {
                FilterOptionsState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                is FilterOptionsState.Error -> Column {
                    Text(
                        text = stringResource(R.string.browse_filter_options_error),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = options.error.message(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetryOptions) { Text(stringResource(DesignR.string.designsystem_retry)) }
                }

                is FilterOptionsState.Loaded -> {
                    ChipGroup(stringResource(R.string.browse_filter_genres)) {
                        options.options.genres.forEach { genre ->
                            ToggleChip(genre, selected = genre in draft.genres) {
                                onDraftChange(draft.copy(genres = draft.genres.toggle(genre)))
                            }
                        }
                    }
                    TagPicker(
                        allTags = options.options.tags.map { it.name },
                        selected = draft.tags,
                        onToggle = { onDraftChange(draft.copy(tags = draft.tags.toggle(it))) }
                    )
                }
            }
            ChipGroup(stringResource(R.string.browse_filter_sort)) {
                ToggleChip(stringResource(R.string.browse_sort_default), selected = draft.sort == null) {
                    onDraftChange(draft.copy(sort = null))
                }
                BrowseSort.entries.forEach { sort ->
                    ToggleChip(stringResource(sort.labelRes()), selected = draft.sort == sort) {
                        onDraftChange(draft.copy(sort = sort))
                    }
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset, enabled = draft.isActive) {
                Text(stringResource(R.string.browse_filter_reset))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onApply() } }) {
                Text(stringResource(R.string.browse_filter_show))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) { chips() }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** "2026" with earlier / later buttons; "Any year" until a year is picked (starts at [currentYear]). */
@Composable
private fun YearStepper(year: Int?, currentYear: Int, onYearChange: (Int?) -> Unit) {
    val last = currentYear + YEARS_AHEAD
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        Text(
            text = stringResource(R.string.browse_filter_year),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onYearChange(((year ?: currentYear + 1) - 1).coerceAtLeast(FIRST_YEAR)) },
                enabled = year == null || year > FIRST_YEAR
            ) {
                Icon(
                    painterResource(TsuzukiIcons.Remove),
                    contentDescription = stringResource(R.string.browse_filter_earlier_year)
                )
            }
            Text(
                text = year?.toString() ?: stringResource(R.string.browse_filter_any_year),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = { onYearChange(((year ?: currentYear - 1) + 1).coerceAtMost(last)) },
                enabled = year == null || year < last
            ) {
                Icon(
                    painterResource(TsuzukiIcons.Add),
                    contentDescription = stringResource(R.string.browse_filter_later_year)
                )
            }
            if (year != null) {
                TextButton(onClick = { onYearChange(null) }) { Text(stringResource(R.string.browse_filter_clear_year)) }
            }
        }
    }
}

/** Picked tags as removable chips, then a search over AniList's ~400 tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPicker(allTags: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val term = search.trim()
    val matches = if (term.isEmpty()) {
        emptyList()
    } else {
        allTags.filter { it !in selected && it.contains(term, ignoreCase = true) }.take(TAG_MATCHES)
    }
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        Text(
            text = stringResource(R.string.browse_filter_tags),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
                selected.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onToggle(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(painterResource(TsuzukiIcons.Close), contentDescription = null) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.browse_filter_tag_search)) },
            leadingIcon = { Icon(painterResource(TsuzukiIcons.Search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (matches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
                matches.forEach { tag -> ToggleChip(tag, selected = false) { onToggle(tag) } }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

internal fun BrowseSort.labelRes(): Int = when (this) {
    BrowseSort.Popularity -> R.string.browse_sort_popularity
    BrowseSort.Trending -> R.string.browse_sort_trending
    BrowseSort.Score -> R.string.browse_sort_score
    BrowseSort.Newest -> R.string.browse_sort_newest
    BrowseSort.RecentlyAdded -> R.string.browse_sort_recently_added
    BrowseSort.Favourites -> R.string.browse_sort_favourites
    BrowseSort.Title -> R.string.browse_sort_title
}
