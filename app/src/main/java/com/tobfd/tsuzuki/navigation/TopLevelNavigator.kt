package com.tobfd.tsuzuki.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.core.ui.ScrollToTopRequest

/** What the last change to the visible back stack was; picks the screen transition. */
enum class NavigationTransition {
    /** A screen was opened: shared axis forward. */
    Forward,

    /** Back, including back to Home from another tab's root and reselect popping to the root. */
    Backward,

    /** Another tab was selected: fade through. */
    TabSwitch
}

/**
 * Navigation state of the app shell: one back stack per [TopLevelTab] and the selected tab. Plain
 * logic on the state holders, so it is unit tested without Compose UI.
 *
 * The visible back stack is Home's stack followed by the current tab's stack, so back from the root of
 * another tab returns to Home, and back on Home's root leaves the app.
 */
@Stable
class TopLevelNavigator(
    private val selectedTab: MutableState<TopLevelTab>,
    private val stacks: Map<TopLevelTab, MutableList<NavKey>>
) {
    /** Pending "scroll to top" per tab, handed to each tab's root screen. */
    val scrollToTopRequests: Map<TopLevelTab, ScrollToTopRequest> =
        TopLevelTab.entries.associateWith { ScrollToTopRequest() }

    val currentTab: TopLevelTab get() = selectedTab.value

    /** Set before each change so the transition spec that runs next can read it. */
    var lastTransition: NavigationTransition = NavigationTransition.Forward
        private set

    /** Tabs whose stacks are shown, bottom to top. */
    val visibleTabs: List<TopLevelTab>
        get() = if (currentTab == TopLevelTab.Home) listOf(TopLevelTab.Home) else listOf(TopLevelTab.Home, currentTab)

    fun stackOf(tab: TopLevelTab): List<NavKey> = stacks.getValue(tab)

    /** Opens [key] on top of the current tab's stack. */
    fun navigate(key: NavKey) {
        lastTransition = NavigationTransition.Forward
        stacks.getValue(currentTab).add(key)
    }

    /**
     * Switches to [tab], keeping every tab's stack. Selecting the current tab again pops it to its
     * root and asks the root screen to scroll to the top.
     */
    fun selectTab(tab: TopLevelTab) {
        if (tab != currentTab) {
            lastTransition = NavigationTransition.TabSwitch
            selectedTab.value = tab
            return
        }
        lastTransition = NavigationTransition.Backward
        val stack = stacks.getValue(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        scrollToTopRequests.getValue(tab).request()
    }

    /** Handles back; returns false when there is nothing left to go back to. */
    fun back(): Boolean {
        val stack = stacks.getValue(currentTab)
        lastTransition = NavigationTransition.Backward
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }

            currentTab != TopLevelTab.Home -> {
                selectedTab.value = TopLevelTab.Home
                true
            }

            else -> false
        }
    }
}
