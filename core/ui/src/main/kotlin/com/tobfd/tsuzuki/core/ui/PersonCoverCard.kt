package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.PersonLite

/**
 * A character or staff member as a 2:3 card with name and one more line, e.g. the media a voiced
 * character is from, for grids and rows next to [MediaCoverCard].
 */
@Composable
fun PersonCoverCard(person: PersonLite, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Column(
        modifier = modifier
            .width(CoverCardWidth)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
    ) {
        MediaCover(imageUrl = person.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth())
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@ThemePreviews
@Composable
private fun PersonCoverCardPreview() {
    TsuzukiPreview {
        Row(
            modifier = Modifier.padding(TsuzukiSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)
        ) {
            PersonCoverCard(PersonLite(176754, "Frieren", null), onClick = {}, detail = "Sousou no Frieren")
            PersonCoverCard(PersonLite(112215, "Atsumi Tanezaki", null), onClick = {})
        }
    }
}
