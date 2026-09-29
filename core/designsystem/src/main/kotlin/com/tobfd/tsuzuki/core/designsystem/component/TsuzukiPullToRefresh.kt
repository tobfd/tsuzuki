package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Material 3 pull to refresh around scrollable [content]; the indicator shows while [isRefreshing]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TsuzukiPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier, content = content)
}
