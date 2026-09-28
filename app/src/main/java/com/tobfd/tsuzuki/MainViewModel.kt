package com.tobfd.tsuzuki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.data.notifications.NotificationsRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.session.expiryWarningDays
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.Viewer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    /** Keeps the splash screen up until the stored session is read. */
    data object Loading : MainUiState

    data object LoggedOut : MainUiState

    data object Guest : MainUiState

    /** [expiryWarningDays] is set within 14 days of the token expiring. */
    data class LoggedIn(val viewer: Viewer, val expiryWarningDays: Long?, val unreadNotificationCount: Int) :
        MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val notificationsRepository: NotificationsRepository,
    private val clock: Clock
) : ViewModel() {
    // Eager, so the splash screen condition can read it before Compose collects.
    val uiState: StateFlow<MainUiState> = combine(
        sessionRepository.session,
        notificationsRepository.unreadCount
    ) { session, unreadCount ->
        when (session) {
            SessionState.Loading -> MainUiState.Loading

            is SessionState.LoggedOut -> MainUiState.LoggedOut

            SessionState.Guest -> MainUiState.Guest

            is SessionState.LoggedIn -> MainUiState.LoggedIn(
                viewer = session.viewer,
                expiryWarningDays = expiryWarningDays(session.expiresAt, clock.instant()),
                unreadNotificationCount = unreadCount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    init {
        viewModelScope.launch { sessionRepository.validate() }
        // A new login (or app start while logged in) gets a fresh badge count right away.
        viewModelScope.launch {
            sessionRepository.session
                .map { (it as? SessionState.LoggedIn)?.viewer?.id }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { notificationsRepository.refreshUnreadCount(force = true) }
        }
    }

    /** Called when the app comes to the foreground; refreshes the badge at most every 5 minutes. */
    fun onAppResumed() {
        if (uiState.value !is MainUiState.LoggedIn) return
        viewModelScope.launch { notificationsRepository.refreshUnreadCount() }
    }

    /** Also used to leave guest mode and to renew an expiring login: both go back to the login screen. */
    fun onLogOut() {
        viewModelScope.launch { sessionRepository.logOut() }
    }
}
