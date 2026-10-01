package com.tobfd.tsuzuki.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * A [PagerState] kept in step with a selection that lives elsewhere (a ViewModel or saved state):
 * the pager starts at [selectedIndex], follows it when it changes from outside, and reports every page
 * it settles on through [onPageSelected], whether reached by swiping or by a tab tap.
 */
@Composable
fun rememberTabPagerState(selectedIndex: Int, pageCount: Int, onPageSelected: (Int) -> Unit): PagerState {
    val state = rememberPagerState(initialPage = selectedIndex.coerceIn(0, maxOf(pageCount - 1, 0))) { pageCount }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onPageSelected)
    LaunchedEffect(state) {
        // The first value is where the pager starts; only later settles are the user's choice.
        snapshotFlow { state.settledPage }.drop(1).collect { page ->
            if (state.pageCount > 0 && page != currentSelected) currentOnSelected(page)
        }
    }
    LaunchedEffect(selectedIndex, pageCount) {
        if (selectedIndex in 0 until pageCount && selectedIndex != state.currentPage && !state.isScrollInProgress) {
            state.scrollToPage(selectedIndex)
        }
    }
    return state
}

/**
 * The tab row above a [TabPager]: tapping a tab scrolls the pager there, and the indicator follows the
 * pager while it is dragged. [scrollable] for tab lists that may not fit the width (status lists plus
 * custom lists), fixed tabs otherwise.
 */
@Composable
fun PagerTabRow(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    edgePadding: Dp = 0.dp,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    tab: @Composable (index: Int, selected: Boolean, onClick: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val selected = pagerState.currentPage.coerceIn(0, maxOf(pagerState.pageCount - 1, 0))
    val indicator: @Composable TabIndicatorScope.() -> Unit = { PagerIndicator(pagerState) }
    val tabs: @Composable () -> Unit = {
        repeat(pagerState.pageCount) { index ->
            tab(index, index == selected) { scope.launch { pagerState.animateScrollToPage(index) } }
        }
    }
    if (scrollable) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selected,
            modifier = modifier,
            edgePadding = edgePadding,
            containerColor = containerColor,
            indicator = indicator,
            tabs = tabs
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = selected,
            modifier = modifier,
            containerColor = containerColor,
            indicator = indicator,
            tabs = tabs
        )
    }
}

/** A plain text tab for [PagerTabRow]. */
@Composable
fun PagerTextTab(label: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit) {
    Tab(selected = selected, onClick = onClick, text = label)
}

/**
 * The primary indicator between the current tab and the one the pager moves towards, as wide as their
 * labels: it follows the finger instead of jumping when a page settles.
 */
@Composable
private fun TabIndicatorScope.PagerIndicator(pagerState: PagerState) {
    TabRowDefaults.PrimaryIndicator(
        width = Dp.Unspecified,
        modifier = Modifier.tabIndicatorLayout { measurable, constraints, positions ->
            if (positions.isEmpty()) return@tabIndicatorLayout layout(0, 0) {}
            val page = pagerState.currentPage.coerceIn(positions.indices)
            val fraction = pagerState.currentPageOffsetFraction
            val towards = (if (fraction > 0) page + 1 else page - 1).coerceIn(positions.indices)
            val from = positions[page]
            val to = positions[towards]
            val progress = abs(fraction)
            val width = lerp(from.contentWidth, to.contentWidth, progress)
            val center = lerp(from.left + from.width / 2, to.left + to.width / 2, progress)
            val widthPx = width.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
            // As wide as the selected tab, so the row places this box at the row's start (no centring
            // offset of its own) and the indicator goes exactly where it is placed here.
            layout(from.width.roundToPx(), placeable.height) {
                placeable.place((center - width / 2).roundToPx(), 0)
            }
        }
    )
}

/**
 * The pages for a [PagerTabRow]. Each page keeps its saveable state (scroll position included) under
 * [pageKey] while it is off screen, and only the visible page plus [beyondViewportPageCount] on either
 * side are composed, so pages that load data load it only when they are about to be seen.
 */
@Composable
fun TabPager(
    pagerState: PagerState,
    pageKey: (page: Int) -> Any,
    modifier: Modifier = Modifier,
    beyondViewportPageCount: Int = 0,
    userScrollEnabled: Boolean = true,
    content: @Composable (page: Int) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = beyondViewportPageCount,
        userScrollEnabled = userScrollEnabled,
        key = pageKey
    ) { page ->
        saveableStateHolder.SaveableStateProvider(pageKey(page)) {
            Box(Modifier.fillMaxSize()) { content(page) }
        }
    }
}
