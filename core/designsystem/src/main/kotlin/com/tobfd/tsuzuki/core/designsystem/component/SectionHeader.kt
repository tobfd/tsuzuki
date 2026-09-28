package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.tobfd.tsuzuki.core.designsystem.R
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview

/** Section title in titleLarge with an optional "See all" text button. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAllClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (onSeeAllClick != null) {
            TextButton(onClick = onSeeAllClick) {
                Text(stringResource(R.string.designsystem_see_all))
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SectionHeaderPreview() {
    TsuzukiPreview {
        SectionHeader(title = "In Progress", onSeeAllClick = {})
        SectionHeader(title = "Up next from Planning")
    }
}
