package com.tobfd.tsuzuki.feature.notifications.alerts

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.core.net.toUri
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

/** The steps of the hint: the notification permission, then the permission for exact alarms. */
private enum class HintStep { Notifications, ExactAlarms, Done }

/**
 * Asks once, after the login, whether Tsuzuki may send notifications, with a short word on what for; then,
 * if Android doesn't allow exact alarms yet, whether new episodes should come right on time. Never on the
 * first start, and never again once answered.
 */
@Composable
fun NotificationPermissionHint() {
    val viewModel = hiltViewModel<NotificationPermissionViewModel>()
    val hintShown by viewModel.hintShown.collectAsStateWithLifecycle()
    if (hintShown != false) return

    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(firstStep(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        step = if (granted && !canScheduleExactAlarms(context)) HintStep.ExactAlarms else HintStep.Done
    }

    when (step) {
        HintStep.Notifications -> NotificationsHintDialog(
            onAllow = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onLater = { step = HintStep.Done }
        )

        HintStep.ExactAlarms -> ExactAlarmHintDialog(
            onOpenSettings = {
                step = HintStep.Done
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                )
            },
            onSkip = { step = HintStep.Done }
        )

        // Answered, or everything was allowed already (e.g. Android 12 has no runtime permission).
        HintStep.Done -> LaunchedEffect(Unit) { viewModel.onHintDone() }
    }
}

private fun firstStep(context: Context): HintStep = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED -> HintStep.Notifications

    !canScheduleExactAlarms(context) -> HintStep.ExactAlarms

    else -> HintStep.Done
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

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

@Composable
private fun ExactAlarmHintDialog(onOpenSettings: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        icon = { Icon(painterResource(R.drawable.ic_stat_tsuzuki), contentDescription = null) },
        title = { Text(stringResource(R.string.alerts_exact_title)) },
        text = { Text(stringResource(R.string.alerts_exact_text)) },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.alerts_exact_allow)) } },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.alerts_exact_skip)) } }
    )
}

@ThemePreviews
@Composable
private fun NotificationsHintDialogPreview() {
    TsuzukiPreview { NotificationsHintDialog(onAllow = {}, onLater = {}) }
}

@ThemePreviews
@Composable
private fun ExactAlarmHintDialogPreview() {
    TsuzukiPreview { ExactAlarmHintDialog(onOpenSettings = {}, onSkip = {}) }
}
