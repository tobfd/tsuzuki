package com.tobfd.tsuzuki.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.common.AniListClientId
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loggingIn: Boolean = false,
    val error: LoginError? = null,
    /** Why the previous session ended, shown above the login button. */
    val logoutReason: LogoutReason? = null
)

sealed interface LoginError {
    data class Api(val error: AppError) : LoginError

    /** The user cancelled or AniList refused on its login page. */
    data object Denied : LoginError

    /** The redirect had no token. */
    data object InvalidRedirect : LoginError

    data object NoBrowser : LoginError
}

sealed interface LoginEffect {
    data class OpenAuthorizePage(val url: String) : LoginEffect
}

private data class LoginProgress(val loggingIn: Boolean = false, val error: LoginError? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    authRedirects: AuthRedirects,
    @AniListClientId private val clientId: String
) : ViewModel() {
    private val progress = MutableStateFlow(LoginProgress())

    val uiState: StateFlow<LoginUiState> = combine(progress, sessionRepository.session) { progress, session ->
        LoginUiState(
            loggingIn = progress.loggingIn,
            error = progress.error,
            logoutReason = (session as? SessionState.LoggedOut)?.reason
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LoginUiState())

    private val effectChannel = Channel<LoginEffect>(Channel.BUFFERED)
    val effects: Flow<LoginEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRedirects.redirects.collect(::handleRedirect)
        }
    }

    fun onLogInClick() {
        if (progress.value.loggingIn) return
        progress.update { it.copy(error = null) }
        effectChannel.trySend(LoginEffect.OpenAuthorizePage(authorizeUrl(clientId)))
    }

    fun onBrowserUnavailable() {
        progress.value = LoginProgress(error = LoginError.NoBrowser)
    }

    fun onBrowseAsGuestClick() {
        viewModelScope.launch { sessionRepository.continueAsGuest() }
    }

    private suspend fun handleRedirect(uri: String) {
        when (val redirect = parseAuthRedirect(uri)) {
            is AuthRedirect.Success -> {
                progress.value = LoginProgress(loggingIn = true)
                val result = sessionRepository.logIn(redirect.accessToken)
                // On success the session switches to LoggedIn and the app leaves this screen.
                progress.value = LoginProgress(
                    error = result.exceptionOrNull()?.let {
                        LoginError.Api(it as? AppError ?: AppError.Unknown(it.message, it))
                    }
                )
            }

            is AuthRedirect.Denied -> progress.value = LoginProgress(error = LoginError.Denied)

            AuthRedirect.Invalid -> progress.value = LoginProgress(error = LoginError.InvalidRedirect)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
