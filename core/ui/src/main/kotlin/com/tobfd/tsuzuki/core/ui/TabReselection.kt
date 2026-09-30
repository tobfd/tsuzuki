package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A "scroll to the top" request for the root screen of a tab, made when the user taps the tab that is
 * already selected. It stays pending until the root screen handles it, so it also reaches a root that
 * only comes back into composition because the reselect popped the tab to its root.
 */
@Stable
class ScrollToTopRequest {
    private val pending = MutableStateFlow(false)

    val isPending: StateFlow<Boolean> = pending.asStateFlow()

    fun request() {
        pending.value = true
    }

    fun consume() {
        pending.value = false
    }
}

/** Provided by the app shell to each tab's root screen; null everywhere else. */
val LocalScrollToTopRequest = staticCompositionLocalOf<ScrollToTopRequest?> { null }

/** Scrolls [listState] to the top whenever the current tab is reselected. */
@Composable
fun ScrollToTopOnTabReselect(listState: LazyListState) = ScrollToTopOnTabReselect(listState) {
    listState.animateScrollToItem(0)
}

/** Scrolls [gridState] to the top whenever the current tab is reselected (grids on large screens). */
@Composable
fun ScrollToTopOnTabReselect(gridState: LazyGridState) = ScrollToTopOnTabReselect(gridState) {
    gridState.animateScrollToItem(0)
}

/** Scrolls [gridState] to the top whenever the current tab is reselected (the Home feed on tablets). */
@Composable
fun ScrollToTopOnTabReselect(gridState: LazyStaggeredGridState) = ScrollToTopOnTabReselect(gridState) {
    gridState.animateScrollToItem(0)
}

@Composable
private fun ScrollToTopOnTabReselect(key: Any, scrollToTop: suspend () -> Unit) {
    val request = LocalScrollToTopRequest.current ?: return
    LaunchedEffect(request, key) {
        request.isPending.collect { pending ->
            if (pending) {
                scrollToTop()
                request.consume()
            }
        }
    }
}
