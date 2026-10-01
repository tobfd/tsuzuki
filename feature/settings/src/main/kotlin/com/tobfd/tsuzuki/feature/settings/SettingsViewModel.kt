package com.tobfd.tsuzuki.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.settings.AniListOptionsChange
import com.tobfd.tsuzuki.core.data.settings.SettingsRepository
import com.tobfd.tsuzuki.core.data.update.UpdateRepository
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppUpdate
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appearance: AppearanceSettings = AppearanceSettings(),
    /** Null for guests: the AniList options and the account need a login. */
    val viewer: Viewer? = null,
    /**
     * The viewer's options as shown: a change being saved shows at once and goes back if AniList refuses
     * it. Null for guests.
     */
    val options: ViewerOptions? = null,
    /** True while a change is on its way to AniList; the AniList controls wait for it. */
    val savingOptions: Boolean = false,
    /** The GitHub update check; null in builds without it. */
    val updates: UpdateSettings? = null
)

/** Settings > About: the manual check and the automatic one. */
data class UpdateSettings(val autoCheck: Boolean = true, val check: UpdateCheck = UpdateCheck.Idle)

/** The state of the manual update check. */
sealed interface UpdateCheck {
    data object Idle : UpdateCheck

    data object Checking : UpdateCheck

    data object UpToDate : UpdateCheck

    data class Available(val update: AppUpdate) : UpdateCheck

    data class Failed(val error: AppError) : UpdateCheck
}

sealed interface SettingsEvent {
    data class SaveFailed(val error: AppError) : SettingsEvent
}

/**
 * Settings (docs/ROADMAP.md, M11). The look is stored on the device and applies at once; title language,
 * score format and adult content are saved to AniList (one request per change) and cached locally.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    sessionRepository: SessionRepository,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val pending = MutableStateFlow<ViewerOptions?>(null)
    private val updateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    private val updates: Flow<UpdateSettings?> = if (updateRepository.isEnabled) {
        combine(updateRepository.autoCheck, updateCheck, ::UpdateSettings)
    } else {
        flowOf(null)
    }

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.appearance,
        sessionRepository.session,
        pending,
        updates
    ) { appearance, session, pending, updates ->
        val viewer = (session as? SessionState.LoggedIn)?.viewer
        SettingsUiState(
            appearance = appearance,
            viewer = viewer,
            options = viewer?.let { pending ?: it.options },
            savingOptions = pending != null,
            updates = updates
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onColorsChange(colors: AppColors) {
        viewModelScope.launch { settingsRepository.setColors(colors) }
    }

    fun onThemeModeChange(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onPureBlackChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPureBlack(enabled) }
    }

    /** "Check for updates": one request to GitHub, the result shows in the row. */
    fun onCheckForUpdates() {
        if (updateCheck.value == UpdateCheck.Checking) return
        updateCheck.value = UpdateCheck.Checking
        viewModelScope.launch {
            updateCheck.value = updateRepository.checkNow().fold(
                onSuccess = { update -> update?.let(UpdateCheck::Available) ?: UpdateCheck.UpToDate },
                onFailure = { UpdateCheck.Failed(it as? AppError ?: AppError.Unknown(it.message, it)) }
            )
        }
    }

    fun onAutoUpdateCheckChange(enabled: Boolean) {
        viewModelScope.launch { updateRepository.setAutoCheck(enabled) }
    }

    fun onTitleLanguageChange(language: TitleLanguage) =
        save(AniListOptionsChange(titleLanguage = language)) { it.copy(titleLanguage = language) }

    fun onScoreFormatChange(format: ScoreFormat) =
        save(AniListOptionsChange(scoreFormat = format)) { it.copy(scoreFormat = format) }

    fun onAdultContentChange(enabled: Boolean) =
        save(AniListOptionsChange(displayAdultContent = enabled)) { it.copy(displayAdultContent = enabled) }

    private fun save(change: AniListOptionsChange, shown: (ViewerOptions) -> ViewerOptions) {
        val current = uiState.value.options ?: return
        if (pending.value != null || shown(current) == current) return
        pending.value = shown(current)
        viewModelScope.launch {
            settingsRepository.updateAniListOptions(change).onFailure { error ->
                events.send(SettingsEvent.SaveFailed(error as? AppError ?: AppError.Unknown(error.message)))
            }
            // On success the cached viewer already carries the new options.
            pending.value = null
        }
    }
}
