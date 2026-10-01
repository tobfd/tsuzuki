package com.tobfd.tsuzuki.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.feature.home.HomeRoute
import com.tobfd.tsuzuki.feature.lists.ListsRoute
import com.tobfd.tsuzuki.feature.media.MediaRoute
import com.tobfd.tsuzuki.feature.notifications.NotificationsRoute
import com.tobfd.tsuzuki.feature.people.CharacterRoute
import com.tobfd.tsuzuki.feature.profile.ProfileRoute
import com.tobfd.tsuzuki.feature.settings.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelNavigatorTest {

    private val selectedTab = mutableStateOf(TopLevelTab.Home)
    private val stacks: Map<TopLevelTab, MutableList<NavKey>> =
        TopLevelTab.entries.associateWith { mutableListOf(it.root) }
    private val navigator = TopLevelNavigator(selectedTab, stacks)

    private fun pendingScroll(tab: TopLevelTab) = navigator.scrollToTopRequests.getValue(tab).isPending.value

    @Test
    fun start_isHomeRootOnly() {
        assertEquals(TopLevelTab.Home, navigator.currentTab)
        assertEquals(listOf(TopLevelTab.Home), navigator.visibleTabs)
        assertEquals(listOf<NavKey>(HomeRoute), navigator.stackOf(TopLevelTab.Home))
    }

    @Test
    fun navigate_pushesOntoTheCurrentTabOnly() {
        navigator.selectTab(TopLevelTab.Lists)
        navigator.navigate(MediaRoute(1))

        assertEquals(listOf(ListsRoute, MediaRoute(1)), navigator.stackOf(TopLevelTab.Lists))
        assertEquals(listOf<NavKey>(HomeRoute), navigator.stackOf(TopLevelTab.Home))
    }

    @Test
    fun open_showsTheTabWithTheScreenOnTopOfItsStack() {
        navigator.selectTab(TopLevelTab.Lists)
        navigator.navigate(MediaRoute(1))
        navigator.selectTab(TopLevelTab.Profile)

        navigator.open(TopLevelTab.Lists, MediaRoute(2))

        assertEquals(TopLevelTab.Lists, navigator.currentTab)
        assertEquals(listOf(ListsRoute, MediaRoute(1), MediaRoute(2)), navigator.stackOf(TopLevelTab.Lists))
        assertEquals(NavigationTransition.TabSwitch, navigator.lastTransition)
    }

    @Test
    fun open_sameScreenAgain_pushesNothing() {
        navigator.open(TopLevelTab.Home, MediaRoute(1))
        navigator.open(TopLevelTab.Home, MediaRoute(1))

        assertEquals(listOf(HomeRoute, MediaRoute(1)), navigator.stackOf(TopLevelTab.Home))
    }

    @Test
    fun open_tabOnly_keepsItsStack() {
        navigator.selectTab(TopLevelTab.Lists)
        navigator.navigate(MediaRoute(1))
        navigator.selectTab(TopLevelTab.Home)

        navigator.open(TopLevelTab.Lists, null)

        assertEquals(listOf(ListsRoute, MediaRoute(1)), navigator.stackOf(TopLevelTab.Lists))
        assertFalse(pendingScroll(TopLevelTab.Lists))
    }

    @Test
    fun switchingTabs_keepsEveryStack() {
        navigator.navigate(MediaRoute(1))
        navigator.selectTab(TopLevelTab.Profile)
        navigator.navigate(SettingsRoute)

        navigator.selectTab(TopLevelTab.Home)

        assertEquals(listOf(HomeRoute, MediaRoute(1)), navigator.stackOf(TopLevelTab.Home))
        assertEquals(listOf(ProfileRoute, SettingsRoute), navigator.stackOf(TopLevelTab.Profile))
    }

    @Test
    fun visibleTabs_areHomeUnderTheCurrentTab() {
        navigator.selectTab(TopLevelTab.Browse)
        assertEquals(listOf(TopLevelTab.Home, TopLevelTab.Browse), navigator.visibleTabs)
    }

    @Test
    fun back_popsTheCurrentTab() {
        navigator.selectTab(TopLevelTab.Lists)
        navigator.navigate(MediaRoute(1))
        navigator.navigate(CharacterRoute(2))

        assertTrue(navigator.back())

        assertEquals(listOf(ListsRoute, MediaRoute(1)), navigator.stackOf(TopLevelTab.Lists))
        assertEquals(TopLevelTab.Lists, navigator.currentTab)
    }

    @Test
    fun backOnAnotherTabsRoot_returnsToHomeAndKeepsThatTab() {
        navigator.navigate(MediaRoute(1))
        navigator.selectTab(TopLevelTab.Profile)

        assertTrue(navigator.back())

        assertEquals(TopLevelTab.Home, navigator.currentTab)
        assertEquals(listOf(HomeRoute, MediaRoute(1)), navigator.stackOf(TopLevelTab.Home))
        assertEquals(listOf<NavKey>(ProfileRoute), navigator.stackOf(TopLevelTab.Profile))
    }

    @Test
    fun backOnHomeRoot_isLeftToTheSystem() {
        assertFalse(navigator.back())
        assertEquals(TopLevelTab.Home, navigator.currentTab)
    }

    @Test
    fun reselectingADeepTab_popsToItsRootAndRequestsScrollToTop() {
        navigator.selectTab(TopLevelTab.Lists)
        navigator.navigate(MediaRoute(1))
        navigator.navigate(CharacterRoute(2))

        navigator.selectTab(TopLevelTab.Lists)

        assertEquals(listOf<NavKey>(ListsRoute), navigator.stackOf(TopLevelTab.Lists))
        assertTrue(pendingScroll(TopLevelTab.Lists))
    }

    @Test
    fun reselectingATabAtItsRoot_requestsScrollToTop() {
        navigator.selectTab(TopLevelTab.Home)

        assertTrue(pendingScroll(TopLevelTab.Home))
        assertFalse(pendingScroll(TopLevelTab.Lists))
    }

    @Test
    fun switchingToAnotherTab_doesNotRequestScroll() {
        navigator.selectTab(TopLevelTab.Browse)

        TopLevelTab.entries.forEach { assertFalse(it.name, pendingScroll(it)) }
    }

    @Test
    fun bellOnAnyTab_opensNotificationsOnThatTab() {
        navigator.selectTab(TopLevelTab.Browse)
        navigator.navigate(NotificationsRoute)

        assertEquals(NotificationsRoute, navigator.stackOf(TopLevelTab.Browse).last())
    }

    @Test
    fun dismiss_closesTheScreenOnTopOnlyOnce() {
        navigator.selectTab(TopLevelTab.Lists)
        navigator.navigate(MediaRoute(1))

        navigator.dismiss(MediaRoute(1))
        navigator.dismiss(MediaRoute(1))

        assertEquals(listOf<NavKey>(ListsRoute), navigator.stackOf(TopLevelTab.Lists))
        assertEquals(TopLevelTab.Lists, navigator.currentTab)
    }

    @Test
    fun dismiss_ignoresAScreenThatIsNotOnTop() {
        navigator.navigate(MediaRoute(1))
        navigator.navigate(CharacterRoute(2))

        navigator.dismiss(MediaRoute(1))

        assertEquals(listOf(HomeRoute, MediaRoute(1), CharacterRoute(2)), navigator.stackOf(TopLevelTab.Home))
    }

    @Test
    fun openingAScreen_isAForwardTransition() {
        navigator.navigate(MediaRoute(1))
        assertEquals(NavigationTransition.Forward, navigator.lastTransition)
    }

    @Test
    fun back_isABackwardTransition() {
        navigator.navigate(MediaRoute(1))
        navigator.back()
        assertEquals(NavigationTransition.Backward, navigator.lastTransition)
    }

    @Test
    fun selectingAnotherTab_isATabSwitch() {
        navigator.selectTab(TopLevelTab.Lists)
        assertEquals(NavigationTransition.TabSwitch, navigator.lastTransition)
    }

    @Test
    fun backFromAnotherTabsRootToHome_isBackwardNotATabSwitch() {
        navigator.selectTab(TopLevelTab.Profile)
        navigator.back()
        assertEquals(NavigationTransition.Backward, navigator.lastTransition)
    }

    @Test
    fun reselectingTheCurrentTab_isBackward() {
        navigator.navigate(MediaRoute(1))
        navigator.selectTab(TopLevelTab.Home)
        assertEquals(NavigationTransition.Backward, navigator.lastTransition)
    }
}
