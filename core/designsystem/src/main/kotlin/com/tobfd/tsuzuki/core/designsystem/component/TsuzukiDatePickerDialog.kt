package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material 3 date picker in a dialog. The picker works in UTC milliseconds; this takes and returns
 * plain dates, so no time zone can shift the day.
 *
 * @param onRemove shows a button that removes the date; null hides it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuzukiDatePickerDialog(
    initialDate: LocalDate?,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            Row {
                if (onRemove != null && removeLabel != null) {
                    TextButton(onClick = onRemove) { Text(removeLabel) }
                }
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        }
    ) {
        DatePicker(state = state)
    }
}
