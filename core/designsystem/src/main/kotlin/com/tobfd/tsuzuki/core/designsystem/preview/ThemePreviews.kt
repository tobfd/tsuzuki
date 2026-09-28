package com.tobfd.tsuzuki.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tobfd.tsuzuki.core.designsystem.theme.ColorSource
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme

/** Light and dark previews. Combine with [TsuzukiPreview] to also cover both color sources. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ThemePreviews

/**
 * Renders [content] once per [ColorSource] (Material You, AniList blue) in the preview's light or
 * dark mode, each on the theme surface.
 */
@Composable
fun TsuzukiPreview(content: @Composable () -> Unit) {
    Column {
        ColorSource.entries.forEach { colorSource ->
            TsuzukiTheme(colorSource = colorSource) {
                Surface {
                    Column(modifier = Modifier.padding(TsuzukiSpacing.small)) {
                        Text(
                            text = colorSource.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        content()
                    }
                }
            }
        }
    }
}
