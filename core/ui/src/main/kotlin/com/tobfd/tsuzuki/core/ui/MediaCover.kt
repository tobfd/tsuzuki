package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing

/** Covers are 2:3 (width:height). */
const val COVER_ASPECT_RATIO = 2f / 3f

/**
 * A media cover: 2:3, medium shape, filled with [placeholderColor] (AniList `coverImage.color`)
 * until the image has loaded, then crossfades in.
 *
 * @param badge optional label in the top-left corner, e.g. "EP 18 / 28" or a status.
 * @param progress optional 0–1 progress shown as a 4 dp bar along the bottom edge.
 */
@Composable
fun MediaCover(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderColor: Color? = null,
    badge: String? = null,
    progress: Float? = null
) {
    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(placeholderColor ?: MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        if (badge != null) {
            CoverBadge(
                text = badge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(TsuzukiSpacing.small)
            )
        }
        if (progress != null) {
            CoverProgressBar(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun CoverBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                horizontal = TsuzukiSpacing.extraSmall,
                vertical = TsuzukiSpacing.extraExtraSmall
            )
        )
    }
}

@Composable
private fun CoverProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TsuzukiSizes.coverProgressBar)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@ThemePreviews
@Composable
private fun MediaCoverPreview() {
    TsuzukiPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.cardGap)) {
            MediaCover(
                imageUrl = null,
                contentDescription = null,
                placeholderColor = Color(0xFFBBF1A1),
                badge = "EP 18 / 28",
                progress = 18f / 28f,
                modifier = Modifier.width(TsuzukiSizes.inProgressCover.width)
            )
            MediaCover(
                imageUrl = null,
                contentDescription = null,
                modifier = Modifier.width(TsuzukiSizes.listThumbnail.width)
            )
        }
    }
}
