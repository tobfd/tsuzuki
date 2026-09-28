package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tobfd.tsuzuki.core.designsystem.R
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes

private const val MAX_BADGE_COUNT = 99

/**
 * Top app bar of the tab roots: title on the left, then the notification bell (badge with the
 * unread count) and the viewer's avatar.
 *
 * @param avatar content of the 32 dp avatar circle, e.g. [InitialAvatar] or a loaded image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuzukiTopBar(
    title: String,
    unreadNotificationCount: Int,
    onNotificationsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        actions = {
            NotificationBell(unreadCount = unreadNotificationCount, onClick = onNotificationsClick)
            val profileDescription = stringResource(R.string.designsystem_profile)
            IconButton(
                onClick = onAvatarClick,
                modifier = Modifier.semantics { contentDescription = profileDescription }
            ) {
                Box(
                    modifier = Modifier
                        .size(TsuzukiSizes.topBarAvatar)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center
                ) {
                    avatar()
                }
            }
        },
        windowInsets = windowInsets
    )
}

@Composable
private fun NotificationBell(unreadCount: Int, onClick: () -> Unit) {
    val description = if (unreadCount > 0) {
        pluralStringResource(R.plurals.designsystem_notifications_unread, unreadCount, unreadCount)
    } else {
        stringResource(R.string.designsystem_notifications)
    }
    // The badge sits outside the IconButton, which clips its content, so "99+" is never cut off.
    // It is offset by the icon's inset in the touch target to stay on the bell's corner, and hidden
    // from TalkBack because the bell's description already includes the count.
    BadgedBox(
        badge = {
            if (unreadCount > 0) {
                Badge(
                    modifier = Modifier
                        .offset(x = -BellIconInset, y = BellIconInset)
                        .clearAndSetSemantics {}
                ) {
                    Text(
                        if (unreadCount > MAX_BADGE_COUNT) {
                            stringResource(R.string.designsystem_badge_overflow)
                        } else {
                            unreadCount.toString()
                        }
                    )
                }
            }
        }
    ) {
        IconButton(onClick = onClick) {
            Icon(painter = painterResource(TsuzukiIcons.Notifications), contentDescription = description)
        }
    }
}

private val BellIconInset = (TsuzukiSizes.minTouchTarget - TsuzukiSizes.icon) / 2

/** 32 dp avatar placeholder: the first letter of [name] on primaryContainer. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(TsuzukiSizes.topBarAvatar),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@ThemePreviews
@Composable
private fun TsuzukiTopBarPreview() {
    TsuzukiPreview {
        TsuzukiTopBar(
            title = "Home",
            unreadNotificationCount = 3,
            onNotificationsClick = {},
            onAvatarClick = {},
            avatar = { InitialAvatar(name = "tobfd") },
            windowInsets = WindowInsets(0)
        )
        TsuzukiTopBar(
            title = "Lists",
            unreadNotificationCount = 0,
            onNotificationsClick = {},
            onAvatarClick = {},
            avatar = { InitialAvatar(name = "tobfd") },
            windowInsets = WindowInsets(0)
        )
    }
}
