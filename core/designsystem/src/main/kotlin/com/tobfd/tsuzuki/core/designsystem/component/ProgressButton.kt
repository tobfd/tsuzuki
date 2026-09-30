package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.tobfd.tsuzuki.core.designsystem.R
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes

/**
 * A tonal button that shows a small spinner in place of its label while [loading]. It keeps its
 * size, and taps are ignored until the work is done.
 */
@Composable
fun ProgressButton(text: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.designsystem_loading)
    FilledTonalButton(
        onClick = { if (!loading) onClick() },
        modifier = modifier.semantics { if (loading) stateDescription = loadingDescription }
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Hidden, not removed, so the button keeps the label's width.
            Text(text = text, modifier = Modifier.alpha(if (loading) 0f else 1f))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(TsuzukiSizes.buttonProgress),
                    color = LocalContentColor.current,
                    strokeWidth = TsuzukiSizes.buttonProgressStroke
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ProgressButtonPreview() {
    TsuzukiPreview {
        ProgressButton(text = "Load more", loading = false, onClick = {})
        ProgressButton(text = "Load more", loading = true, onClick = {})
    }
}
