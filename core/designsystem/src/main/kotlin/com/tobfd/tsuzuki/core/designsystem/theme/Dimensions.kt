package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Spacing on the 4 dp grid from design/tokens.json (spacing). Features never use raw dp for spacing. */
object TsuzukiSpacing {
    /** Half step, only for tight insides such as badge padding. */
    val extraExtraSmall = 2.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp

    val screenMargin = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val homeSectionGap = 28.dp
    val chipPadding = 8.dp
}

/** Component sizes from design/tokens.json (components) and docs/DESIGN.md. */
object TsuzukiSizes {
    val minTouchTarget = 48.dp
    val icon = 24.dp
    val inProgressCover = DpSize(144.dp, 204.dp)
    val listThumbnail = DpSize(48.dp, 68.dp)
    val plusOneButton = 48.dp
    val plusOneButtonDense = 40.dp
    val topAppBarHeight = 64.dp
    val bottomNavHeight = 80.dp
    val topBarAvatar = 32.dp
    val stateIconTile = 56.dp
    val statusDot = 8.dp
    val coverProgressBar = 4.dp
    val primaryButtonHeight = 56.dp
    val logoTile = 96.dp
    val buttonProgress = 20.dp
    val buttonProgressStroke = 2.dp
}
