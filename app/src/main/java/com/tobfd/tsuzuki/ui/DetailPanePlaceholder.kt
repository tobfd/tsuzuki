package com.tobfd.tsuzuki.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tobfd.tsuzuki.R
import com.tobfd.tsuzuki.core.designsystem.component.EmptyState
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme

/**
 * The detail pane before anything is opened (Lists, Browse or a profile beside it on a tablet), so
 * the right half of the screen isn't simply empty.
 */
@Composable
fun DetailPanePlaceholder(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(contentAlignment = Alignment.Center) {
            EmptyState(
                icon = painterResource(TsuzukiIcons.LogoGlyph),
                title = stringResource(R.string.detail_placeholder_title),
                message = stringResource(R.string.detail_placeholder_message)
            )
        }
    }
}

@ThemePreviews
@Composable
private fun DetailPanePlaceholderPreview() {
    TsuzukiTheme { DetailPanePlaceholder() }
}
