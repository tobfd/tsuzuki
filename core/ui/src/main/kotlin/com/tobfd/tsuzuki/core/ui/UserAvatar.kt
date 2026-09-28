package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tobfd.tsuzuki.core.designsystem.component.InitialAvatar
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes

/**
 * A user's avatar in a circle. Shows the initial of [name] until the image has loaded, and when
 * there is no image or it fails. Decorative: the surrounding control describes it.
 */
@Composable
fun UserAvatar(avatarUrl: String?, name: String, modifier: Modifier = Modifier, size: Dp = TsuzukiSizes.topBarAvatar) {
    Box(modifier = modifier.size(size).clip(CircleShape)) {
        InitialAvatar(name = name, modifier = Modifier.matchParentSize())
        if (avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@ThemePreviews
@Composable
private fun UserAvatarPreview() {
    TsuzukiPreview {
        UserAvatar(avatarUrl = null, name = "tobfd")
    }
}
