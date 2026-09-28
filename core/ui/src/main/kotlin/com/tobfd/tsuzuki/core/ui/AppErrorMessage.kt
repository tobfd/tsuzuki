package com.tobfd.tsuzuki.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tobfd.tsuzuki.core.common.AppError

/** User-facing text for an [AppError]; every screen and snackbar uses these. */
@Composable
@ReadOnlyComposable
fun AppError.message(): String = when (this) {
    AppError.Offline -> stringResource(R.string.ui_error_offline)

    is AppError.RateLimited ->
        pluralStringResource(R.plurals.ui_error_rate_limited, retryAfterSec.toInt(), retryAfterSec.toInt())

    AppError.ApiUnavailable -> stringResource(R.string.ui_error_api_unavailable)

    AppError.Unauthorized -> stringResource(R.string.ui_error_unauthorized)

    is AppError.Validation -> stringResource(R.string.ui_error_validation)

    AppError.NotFound -> stringResource(R.string.ui_error_not_found)

    is AppError.Unknown -> stringResource(R.string.ui_error_unknown)
}
