package com.tobfd.tsuzuki.feature.lists.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.designsystem.component.StatusChip
import com.tobfd.tsuzuki.core.designsystem.component.TsuzukiDatePickerDialog
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.ui.MediaCover
import com.tobfd.tsuzuki.core.ui.PreviewListEntries
import com.tobfd.tsuzuki.core.ui.R as UiR
import com.tobfd.tsuzuki.core.ui.coverColorOrNull
import com.tobfd.tsuzuki.core.ui.labelRes
import com.tobfd.tsuzuki.core.ui.score.ScoreText
import com.tobfd.tsuzuki.core.ui.statusColor
import com.tobfd.tsuzuki.feature.lists.R
import com.tobfd.tsuzuki.feature.lists.STATUS_TAB_ORDER
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/**
 * The list editor for the viewer's entry of a media. The app shows it in a bottom sheet
 * (`ModalBottomSheet`, with its own predictive back); the detail page opens it too (M6).
 */
@Serializable
data class ListEditorRoute(val mediaId: Int) : NavKey

@Composable
fun ListEditorSheet(mediaId: Int, onDismiss: () -> Unit, onOpenDetails: (mediaId: Int) -> Unit) {
    val viewModel = hiltViewModel<ListEditorViewModel, ListEditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(mediaId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.close.collect { onDismiss() } }
    when (val current = state) {
        ListEditorUiState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 200.dp))

        is ListEditorUiState.Editing -> ListEditorContent(
            state = current,
            actions = ListEditorActions(
                onStatusChange = viewModel::onStatusChange,
                onProgressChange = viewModel::onProgressChange,
                onVolumesChange = viewModel::onVolumesChange,
                onScoreChange = viewModel::onScoreChange,
                onStartedAtChange = viewModel::onStartedAtChange,
                onCompletedAtChange = viewModel::onCompletedAtChange,
                onRepeatChange = viewModel::onRepeatChange,
                onNotesChange = viewModel::onNotesChange,
                onPrivateChange = viewModel::onPrivateChange,
                onHiddenChange = viewModel::onHiddenChange,
                onCustomListToggle = viewModel::onCustomListToggle,
                onSave = viewModel::onSave,
                onRemove = viewModel::onRemove,
                onOpenDetails = { onOpenDetails(mediaId) }
            )
        )
    }
}

/** Callbacks of the editor, bundled to keep the content's signature readable. */
class ListEditorActions(
    val onStatusChange: (MediaListStatus) -> Unit = {},
    val onProgressChange: (Int) -> Unit = {},
    val onVolumesChange: (Int) -> Unit = {},
    val onScoreChange: (Double) -> Unit = {},
    val onStartedAtChange: (FuzzyDate?) -> Unit = {},
    val onCompletedAtChange: (FuzzyDate?) -> Unit = {},
    val onRepeatChange: (Int) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onPrivateChange: (Boolean) -> Unit = {},
    val onHiddenChange: (Boolean) -> Unit = {},
    val onCustomListToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onOpenDetails: () -> Unit = {}
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEditorContent(state: ListEditorUiState.Editing, actions: ListEditorActions, modifier: Modifier = Modifier) {
    val entry = state.entry
    val form = state.form
    val type = entry.type
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TsuzukiSpacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)
        ) {
            Header(state, actions.onOpenDetails)

            Section(stringResource(R.string.lists_editor_status)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)
                ) {
                    STATUS_TAB_ORDER.forEach { status ->
                        StatusChip(
                            label = stringResource(status.labelRes(type)),
                            statusColor = status.statusColor(),
                            selected = form.status == status,
                            onClick = { actions.onStatusChange(status) }
                        )
                    }
                }
            }

            NumberStepper(
                label = stringResource(
                    if (type ==
                        MediaType.ANIME
                    ) {
                        R.string.lists_editor_progress_episodes
                    } else {
                        R.string.lists_editor_progress_chapters
                    }
                ),
                value = form.progress,
                total = entry.media.total,
                onValueChange = actions.onProgressChange,
                error = state.errors[EditorField.Progress]
            )
            if (type == MediaType.MANGA) {
                NumberStepper(
                    label = stringResource(R.string.lists_editor_volumes),
                    value = form.progressVolumes,
                    total = entry.media.volumes,
                    onValueChange = actions.onVolumesChange,
                    error = state.errors[EditorField.Volumes]
                )
            }

            Section(stringResource(R.string.lists_editor_score)) {
                ScoreControl(format = state.scoreFormat, score = form.score, onScoreChange = actions.onScoreChange)
                FieldErrorText(state.errors[EditorField.Score])
            }

            MoreToggle(expanded = moreExpanded, onToggle = { moreExpanded = !moreExpanded })
            if (moreExpanded) {
                MoreSection(state, actions)
            }

            (state.generalError ?: if (state.rejected) stringResource(UiR.string.ui_error_validation) else null)?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuzukiSpacing.screenMargin, vertical = TsuzukiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { confirmRemove = true },
                enabled = !state.saving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.lists_editor_remove))
            }
            Box(Modifier.weight(1f))
            Button(
                onClick = actions.onSave,
                enabled =
                    !state.saving && state.errors.values.none { it is FieldError.TooHigh }
            ) {
                if (state.saving) {
                    val savingLabel = stringResource(R.string.lists_editor_saving)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = savingLabel },
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.lists_editor_save))
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.lists_editor_remove_title, entry.media.title.userPreferred)) },
            text = { Text(stringResource(R.string.lists_editor_remove_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        actions.onRemove()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.lists_editor_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.lists_editor_cancel)) }
            }
        )
    }
}

@Composable
private fun Header(state: ListEditorUiState.Editing, onOpenDetails: () -> Unit) {
    val media = state.entry.media
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)
    ) {
        MediaCover(
            imageUrl = media.coverUrl,
            contentDescription = null,
            placeholderColor = coverColorOrNull(media.coverColor),
            modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title.userPreferred,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(media.format?.let { stringResource(it.labelRes()) }, media.year?.toString())
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onOpenDetails) { Text(stringResource(R.string.lists_editor_details)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun FieldErrorText(error: FieldError?) {
    val text = when (error) {
        null -> return
        is FieldError.TooHigh -> stringResource(R.string.lists_editor_too_high, error.max)
        is FieldError.Server -> error.message
    }
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

/** − value / total +, with the number also typeable. */
@Composable
private fun NumberStepper(label: String, value: Int, total: Int?, onValueChange: (Int) -> Unit, error: FieldError?) {
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { onValueChange(value - 1) }, enabled = value > 0) {
                Icon(
                    painterResource(TsuzukiIcons.Remove),
                    contentDescription = stringResource(R.string.lists_editor_decrease)
                )
            }
            var text by remember(value) { mutableStateOf(value.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit).take(MAX_DIGITS)
                    text = digits
                    digits.toIntOrNull()?.let(onValueChange)
                },
                modifier = Modifier
                    .width(88.dp)
                    .semantics { contentDescription = label },
                singleLine = true,
                isError = error != null,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (total != null) {
                Text(
                    text = "/ $total",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = TsuzukiSpacing.small)
                )
            }
            IconButton(onClick = { onValueChange(value + 1) }, enabled = total == null || value < total) {
                Icon(
                    painterResource(TsuzukiIcons.Add),
                    contentDescription = stringResource(R.string.lists_editor_increase)
                )
            }
        }
        FieldErrorText(error)
    }
}

private const val MAX_DIGITS = 5

private class Smiley(val value: Int, val icon: Int, val label: Int)

private val SMILEYS = listOf(
    Smiley(1, TsuzukiIcons.SentimentDissatisfied, UiR.string.ui_score_disliked),
    Smiley(2, TsuzukiIcons.SentimentNeutral, UiR.string.ui_score_neutral),
    Smiley(3, TsuzukiIcons.SentimentSatisfied, UiR.string.ui_score_liked)
)

/** The score control for the viewer's format (docs/DESIGN.md, "List editor"). */
@Composable
private fun ScoreControl(format: ScoreFormat, score: Double, onScoreChange: (Double) -> Unit) {
    when (format) {
        ScoreFormat.POINT_100 -> ScoreSlider(
            score,
            max = 100f,
            steps = 99,
            format = format,
            onScoreChange = onScoreChange
        )

        ScoreFormat.POINT_10 -> ScoreSlider(score, max = 10f, steps = 9, format = format, onScoreChange = onScoreChange)

        // Half points, as designed; finer scores set on the website stay until the slider is moved.
        ScoreFormat.POINT_10_DECIMAL -> ScoreSlider(
            score,
            max = 10f,
            steps = 19,
            format = format,
            onScoreChange = onScoreChange
        )

        ScoreFormat.POINT_5 -> Row {
            (1..5).forEach { star ->
                val filled = score >= star
                val description = pluralStringResource(R.plurals.lists_editor_stars, star, star)
                IconToggleButton(
                    checked = score.roundToInt() == star,
                    onCheckedChange = { onScoreChange(if (score.roundToInt() == star) 0.0 else star.toDouble()) },
                    modifier = Modifier.semantics { contentDescription = description }
                ) {
                    Icon(
                        painter = painterResource(if (filled) TsuzukiIcons.StarFilled else TsuzukiIcons.Star),
                        contentDescription = null,
                        tint = activeTint(filled)
                    )
                }
            }
        }

        ScoreFormat.POINT_3 -> Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small)) {
            SMILEYS.forEach { smiley ->
                val value = smiley.value
                val icon = smiley.icon
                val selected = score.roundToInt() == value
                val description = stringResource(smiley.label)
                IconToggleButton(
                    checked = selected,
                    onCheckedChange = { onScoreChange(if (selected) 0.0 else value.toDouble()) },
                    modifier = Modifier.semantics { contentDescription = description }
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = activeTint(selected)
                    )
                }
            }
        }
    }
}

@Composable
private fun activeTint(active: Boolean) =
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun ScoreSlider(score: Double, max: Float, steps: Int, format: ScoreFormat, onScoreChange: (Double) -> Unit) {
    Column {
        Box(modifier = Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
            if (score > 0) {
                ScoreText(score = score, format = format, style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    text = stringResource(R.string.lists_editor_not_scored),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = score.toFloat().coerceIn(0f, max),
            onValueChange = { onScoreChange(roundToStep(it.toDouble(), max / (steps + 1))) },
            valueRange = 0f..max,
            steps = steps
        )
    }
}

private fun roundToStep(value: Double, step: Float): Double = (value / step).roundToInt() * step.toDouble()

@Composable
private fun MoreToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = expanded, role = Role.Switch, onValueChange = { onToggle() })
            .padding(vertical = TsuzukiSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (expanded) R.string.lists_editor_less else R.string.lists_editor_more),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painterResource(if (expanded) TsuzukiIcons.ExpandLess else TsuzukiIcons.ExpandMore),
            contentDescription = null
        )
    }
}

@Composable
private fun MoreSection(state: ListEditorUiState.Editing, actions: ListEditorActions) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.large)) {
        DateField(
            label = stringResource(R.string.lists_editor_start_date),
            date = form.startedAt,
            onDateChange = actions.onStartedAtChange,
            error = state.errors[EditorField.StartedAt]
        )
        DateField(
            label = stringResource(R.string.lists_editor_finish_date),
            date = form.completedAt,
            onDateChange = actions.onCompletedAtChange,
            error = state.errors[EditorField.CompletedAt]
        )
        NumberStepper(
            label = stringResource(
                if (state.entry.type ==
                    MediaType.ANIME
                ) {
                    R.string.lists_editor_rewatches
                } else {
                    R.string.lists_editor_rereads
                }
            ),
            value = form.repeat,
            total = null,
            onValueChange = actions.onRepeatChange,
            error = state.errors[EditorField.Repeat]
        )
        Column {
            OutlinedTextField(
                value = form.notes,
                onValueChange = actions.onNotesChange,
                label = { Text(stringResource(R.string.lists_editor_notes)) },
                isError = state.errors[EditorField.Notes] != null,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            FieldErrorText(state.errors[EditorField.Notes])
        }
        SwitchRow(
            title = stringResource(R.string.lists_editor_private),
            hint = stringResource(R.string.lists_editor_private_hint),
            checked = form.isPrivate,
            onCheckedChange = actions.onPrivateChange
        )
        SwitchRow(
            title = stringResource(R.string.lists_editor_hidden),
            hint = stringResource(R.string.lists_editor_hidden_hint),
            checked = form.hiddenFromStatusLists,
            onCheckedChange = actions.onHiddenChange
        )
        if (state.customListNames.isNotEmpty()) {
            Section(stringResource(R.string.lists_editor_custom_lists)) {
                state.customListNames.forEach { name ->
                    val checked = name in form.customLists
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { actions.onCustomListToggle(name, it) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateField(label: String, date: FuzzyDate?, onDateChange: (FuzzyDate?) -> Unit, error: FieldError?) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { picking = true }) {
                Icon(
                    painter = painterResource(TsuzukiIcons.CalendarToday),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = date?.let { formatFuzzyDate(it) } ?: stringResource(R.string.lists_editor_date_not_set),
                    modifier = Modifier.padding(start = TsuzukiSpacing.small)
                )
            }
        }
        FieldErrorText(error)
    }
    if (picking) {
        TsuzukiDatePickerDialog(
            initialDate = date?.toLocalDateOrNull(),
            confirmLabel = stringResource(R.string.lists_editor_date_ok),
            dismissLabel = stringResource(R.string.lists_editor_cancel),
            onConfirm = {
                picking = false
                onDateChange(FuzzyDate.of(it))
            },
            onDismiss = { picking = false },
            removeLabel = stringResource(R.string.lists_editor_date_remove),
            onRemove = if (date != null) {
                {
                    picking = false
                    onDateChange(null)
                }
            } else {
                null
            }
        )
    }
}

/** A full date in the locale's medium style; partial AniList dates as far as they are known. */
@Composable
private fun formatFuzzyDate(date: FuzzyDate): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    date.toLocalDateOrNull()?.let {
        return it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    val year = date.year ?: return "?"
    val month = date.month ?: return year.toString()
    return java.time.YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@ThemePreviews
@Composable
private fun ListEditorContentPreview() {
    val entry = PreviewListEntries.frieren
    TsuzukiPreview {
        ListEditorContent(
            state = ListEditorUiState.Editing(
                entry = entry,
                form = ListEditorForm.of(entry, ScoreFormat.POINT_10_DECIMAL),
                scoreFormat = ScoreFormat.POINT_10_DECIMAL,
                customListNames = persistentListOf("Favs")
            ),
            actions = ListEditorActions()
        )
    }
}
