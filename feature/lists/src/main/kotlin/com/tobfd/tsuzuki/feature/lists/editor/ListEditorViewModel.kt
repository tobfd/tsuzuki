package com.tobfd.tsuzuki.feature.lists.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.data.list.ChangeState
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.list.NetworkMonitor
import com.tobfd.tsuzuki.core.data.list.RejectReason
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.fromRaw
import com.tobfd.tsuzuki.core.model.toRaw
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the editor lets the user change, in the viewer's score format. */
data class ListEditorForm(
    val status: MediaListStatus,
    val progress: Int,
    val progressVolumes: Int,
    /** In the viewer's score format; 0 is "not scored". */
    val score: Double,
    val startedAt: FuzzyDate?,
    val completedAt: FuzzyDate?,
    val repeat: Int,
    val notes: String,
    val isPrivate: Boolean,
    val hiddenFromStatusLists: Boolean,
    val customLists: Set<String>
) {
    fun applyTo(entry: MediaListEntry, format: ScoreFormat): MediaListEntry = entry.copy(
        status = status,
        progress = progress,
        progressVolumes = progressVolumes,
        // Keep the exact raw score when the shown score didn't change (e.g. 87 shown as 9).
        scoreRaw = if (score == format.fromRaw(entry.scoreRaw)) entry.scoreRaw else format.toRaw(score),
        startedAt = startedAt,
        completedAt = completedAt,
        repeat = repeat,
        notes = notes,
        isPrivate = isPrivate,
        hiddenFromStatusLists = hiddenFromStatusLists,
        customLists = customLists
    )

    companion object {
        fun of(entry: MediaListEntry, format: ScoreFormat) = ListEditorForm(
            status = entry.status,
            progress = entry.progress,
            progressVolumes = entry.progressVolumes,
            score = format.fromRaw(entry.scoreRaw),
            startedAt = entry.startedAt,
            completedAt = entry.completedAt,
            repeat = entry.repeat,
            notes = entry.notes,
            isPrivate = entry.isPrivate,
            hiddenFromStatusLists = entry.hiddenFromStatusLists,
            customLists = entry.customLists
        )
    }
}

/** Fields that can show an error under them. */
enum class EditorField {
    Progress,
    Volumes,
    Score,
    Repeat,
    Notes,
    StartedAt,
    CompletedAt
}

/** An error below a field: over [max] (checked here), or AniList's own [message]. */
sealed interface FieldError {
    data class TooHigh(val max: Int) : FieldError

    data class Server(val message: String) : FieldError
}

sealed interface ListEditorUiState {
    data object Loading : ListEditorUiState

    data class Editing(
        val entry: MediaListEntry,
        val form: ListEditorForm,
        val scoreFormat: ScoreFormat,
        /** All the viewer's custom lists for this list type. */
        val customListNames: ImmutableList<String>,
        val saving: Boolean = false,
        val errors: ImmutableMap<EditorField, FieldError> = persistentMapOf(),
        /** AniList's message when it couldn't be tied to a field. */
        val generalError: String? = null,
        /** True when AniList rejected the change without saying why. */
        val rejected: Boolean = false
    ) : ListEditorUiState {
        val hasChanges: Boolean
            get() = !EntryChanges.between(entry, form.applyTo(entry, scoreFormat)).isEmpty()
    }
}

/** Waits this long for AniList's answer to a save before closing anyway (the change stays queued). */
private const val SAVE_WAIT_MS = 10_000L

/**
 * The list editor (docs/DESIGN.md, "List editor"). Saving writes to Room at once and queues the
 * change; while online the sheet waits briefly for AniList, so validation errors show inline.
 * Offline it closes right away and the change is sent later.
 */
@HiltViewModel(assistedFactory = ListEditorViewModel.Factory::class)
class ListEditorViewModel @AssistedInject constructor(
    @Assisted private val mediaId: Int,
    private val listRepository: ListRepository,
    private val networkMonitor: NetworkMonitor,
    private val sessionRepository: SessionRepository,
    private val clock: Clock
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(mediaId: Int): ListEditorViewModel
    }

    private val state = MutableStateFlow<ListEditorUiState>(ListEditorUiState.Loading)
    val uiState: StateFlow<ListEditorUiState> = state.asStateFlow()

    private val closeEvents = Channel<Unit>(Channel.CONFLATED)

    /** Emits when the sheet should close: saved, removed, or the entry is gone. */
    val close: Flow<Unit> = closeEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val entry = listRepository.observeEntry(mediaId).first()
            if (entry == null) {
                closeEvents.send(Unit)
                return@launch
            }
            val format = (sessionRepository.session.first() as? SessionState.LoggedIn)?.viewer?.options?.scoreFormat
                ?: ScoreFormat.POINT_10_DECIMAL
            val customLists = listRepository.observeList(entry.type).first().customLists
            state.value = ListEditorUiState.Editing(
                entry = entry,
                form = ListEditorForm.of(entry, format),
                scoreFormat = format,
                customListNames = customLists.toImmutableList()
            )
        }
    }

    private fun edit(change: (ListEditorUiState.Editing) -> ListEditorForm) {
        state.update { current ->
            if (current !is ListEditorUiState.Editing || current.saving) return@update current
            val form = change(current)
            current.copy(form = form, errors = validate(current.entry, form), generalError = null, rejected = false)
        }
    }

    /**
     * Like AniList: finishing fills in the total and today's finish date, starting a planned entry
     * sets today's start date (only where the user hasn't set one).
     */
    fun onStatusChange(status: MediaListStatus) = edit { current ->
        val form = current.form
        val today = FuzzyDate.of(today())
        when (status) {
            MediaListStatus.COMPLETED -> form.copy(
                status = status,
                progress = current.entry.media.total ?: form.progress,
                completedAt = form.completedAt ?: today
            )

            MediaListStatus.CURRENT -> form.copy(
                status = status,
                startedAt = if (form.status == MediaListStatus.PLANNING) form.startedAt ?: today else form.startedAt
            )

            else -> form.copy(status = status)
        }
    }

    fun onProgressChange(progress: Int) = edit { it.form.copy(progress = progress.coerceAtLeast(0)) }

    fun onVolumesChange(volumes: Int) = edit { it.form.copy(progressVolumes = volumes.coerceAtLeast(0)) }

    fun onScoreChange(score: Double) = edit { it.form.copy(score = score.coerceAtLeast(0.0)) }

    fun onStartedAtChange(date: FuzzyDate?) = edit { it.form.copy(startedAt = date) }

    fun onCompletedAtChange(date: FuzzyDate?) = edit { it.form.copy(completedAt = date) }

    fun onRepeatChange(repeat: Int) = edit { it.form.copy(repeat = repeat.coerceAtLeast(0)) }

    fun onNotesChange(notes: String) = edit { it.form.copy(notes = notes) }

    fun onPrivateChange(isPrivate: Boolean) = edit { it.form.copy(isPrivate = isPrivate) }

    fun onHiddenChange(hidden: Boolean) = edit { it.form.copy(hiddenFromStatusLists = hidden) }

    fun onCustomListToggle(name: String, enabled: Boolean) = edit { current ->
        val lists = current.form.customLists
        current.form.copy(customLists = if (enabled) lists + name else lists - name)
    }

    fun onSave() {
        val current = state.value as? ListEditorUiState.Editing ?: return
        if (current.saving || current.errors.values.any { it is FieldError.TooHigh }) return
        val edited = current.form.applyTo(current.entry, current.scoreFormat)
        val changes = EntryChanges.between(current.entry, edited)
        state.value = current.copy(saving = true)
        viewModelScope.launch {
            val changeId = listRepository.update(current.entry.id, changes)
            if (changeId == null || !networkMonitor.isOnline()) {
                closeEvents.send(Unit)
                return@launch
            }
            val result = withTimeoutOrNull(SAVE_WAIT_MS) {
                listRepository.observeChange(changeId).first { it !is ChangeState.Queued }
            }
            val change = (result as? ChangeState.Rejected)?.change
            // Gone from the list: close, the Lists screen says why.
            if (change == null || change.reason == RejectReason.NotFound) {
                closeEvents.send(Unit)
                return@launch
            }
            // Shown here, so the Lists screen doesn't repeat it. The form keeps what the user typed.
            listRepository.dismissRejection(current.entry.id)
            val fieldErrors = serverFieldErrors(change.fields)
            state.value = current.copy(
                saving = false,
                errors = (current.errors + fieldErrors).toImmutableMap(),
                generalError = change.detail.takeIf { fieldErrors.isEmpty() },
                rejected = fieldErrors.isEmpty() && change.detail == null
            )
        }
    }

    fun onRemove() {
        val current = state.value as? ListEditorUiState.Editing ?: return
        viewModelScope.launch {
            listRepository.delete(current.entry.id)
            closeEvents.send(Unit)
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
}

/** Progress and volumes can't go past the known totals. */
internal fun validate(entry: MediaListEntry, form: ListEditorForm): ImmutableMap<EditorField, FieldError> {
    val errors = mutableMapOf<EditorField, FieldError>()
    entry.media.total?.let { total ->
        if (form.progress >
            total
        ) {
            errors[EditorField.Progress] = FieldError.TooHigh(total)
        }
    }
    entry.media.volumes?.let { volumes ->
        if (form.progressVolumes > volumes) errors[EditorField.Volumes] = FieldError.TooHigh(volumes)
    }
    return errors.toImmutableMap()
}

/** AniList names fields like the mutation's variables (`progress`, `scoreRaw`, `startedAt.year`). */
internal fun serverFieldErrors(fields: Map<String, List<String>>): Map<EditorField, FieldError> = buildMap {
    fields.forEach { (name, messages) ->
        val message = messages.firstOrNull() ?: return@forEach
        val field = when {
            name.startsWith("progressVolumes") -> EditorField.Volumes
            name.startsWith("progress") -> EditorField.Progress
            name.startsWith("score") -> EditorField.Score
            name.startsWith("repeat") -> EditorField.Repeat
            name.startsWith("notes") -> EditorField.Notes
            name.startsWith("startedAt") -> EditorField.StartedAt
            name.startsWith("completedAt") -> EditorField.CompletedAt
            else -> null
        } ?: return@forEach
        put(field, FieldError.Server(message))
    }
}
