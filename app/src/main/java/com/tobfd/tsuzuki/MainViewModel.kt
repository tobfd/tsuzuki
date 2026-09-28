package com.tobfd.tsuzuki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.session.expiryWarningDays
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.Viewer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    /** Keeps the splash screen up until the stored session is read. */
    data object Loading : MainUiState

    data object LoggedOut : MainUiState

    data object Guest : MainUiState

    /** [expiryWarningDays] is set within 14 days of the token expiring. */
    data class LoggedIn(val viewer: Viewer, val expiryWarningDays: Long?) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(private val sessionRepository: SessionRepository, private val clock: Clock) :
    ViewModel() {
    // Eager, so the splash screen condition can read it before Compose collects.
    val uiState: StateFlow<MainUiState> = sessionRepository.session
        .map { session ->
            when (session) {
                SessionState.Loading -> MainUiState.Loading

                is SessionState.LoggedOut -> MainUiState.LoggedOut

                SessionState.Guest -> MainUiState.Guest

                is SessionState.LoggedIn -> MainUiState.LoggedIn(
                    viewer = session.viewer,
                    expiryWarningDays = expiryWarningDays(session.expiresAt, clock.instant())
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    init {
        viewModelScope.launch { sessionRepository.validate() }
    }

    /** Also used to leave guest mode and to renew an expiring login: both go back to the login screen. */
    fun onLogOut() {
        viewModelScope.launch { sessionRepository.logOut() }
    }
}
