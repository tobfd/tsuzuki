package com.tobfd.tsuzuki.feature.notifications.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tobfd.tsuzuki.core.data.notifications.AlertsRepository
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.feature.notifications.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationPermissionViewModel @Inject constructor(
    private val alertsRepository: AlertsRepository,
    private val coordinator: AlertCoordinator
) : ViewModel() {
    /** Null until read, so nothing flashes up. */
    val hintShown: StateFlow<Boolean?> =
        alertsRepository.permissionHintShown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The hint is done (whatever the answer): it never comes again, and the plan follows the new permissions. */
    fun onHintDone() {
        viewModelScope.launch {
            alertsRepository.markPermissionHintShown()
            coordinator.replan()
        }
    }
}

/**
 * Asks once, after the login, whether Tsuzuki may send notifications, with a short word on what for.
 * Never on the first start, and never again once answered. The only permission the notifications need.
 */
@Composable
fun NotificationPermissionHint() {
    val viewModel = hiltViewModel<NotificationPermissionViewModel>()
    val hintShown by viewModel.hintShown.collectAsStateWithLifecycle()
    if (hintShown != false) return

    val context = LocalContext.current
    var asking by rememberSaveable { mutableStateOf(needsPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asking = false
    }

    if (asking && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        NotificationsHintDialog(
            onAllow = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onLater = { asking = false }
        )
    } else {
        // Answered, or nothing to ask (Android 12 has no runtime permission, or it was granted already).
        LaunchedEffect(Unit) { viewModel.onHintDone() }
    }
}

private fun needsPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
    PackageManager.PERMISSION_GRANTED

@Composable
private fun NotificationsHintDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = { Icon(painterResource(R.drawable.ic_stat_tsuzuki), contentDescription = null) },
        title = { Text(stringResource(R.string.alerts_hint_title)) },
        text = { Text(stringResource(R.string.alerts_hint_text)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.alerts_hint_allow)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.alerts_hint_later)) } }
    )
}

@ThemePreviews
@Composable
private fun NotificationsHintDialogPreview() {
    TsuzukiPreview { NotificationsHintDialog(onAllow = {}, onLater = {}) }
}
