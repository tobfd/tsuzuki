package com.tobfd.tsuzuki.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tobfd.tsuzuki.core.ui.LocalScrollToTopRequest
import com.tobfd.tsuzuki.feature.browse.BrowseRoute
import com.tobfd.tsuzuki.feature.browse.BrowseScreen
import com.tobfd.tsuzuki.feature.home.HomeRoute
import com.tobfd.tsuzuki.feature.home.HomeScreen
import com.tobfd.tsuzuki.feature.lists.ListsRoute
import com.tobfd.tsuzuki.feature.lists.ListsScreen
import com.tobfd.tsuzuki.feature.media.MediaRoute
import com.tobfd.tsuzuki.feature.media.MediaScreen
import com.tobfd.tsuzuki.feature.notifications.NotificationsRoute
import com.tobfd.tsuzuki.feature.notifications.NotificationsScreen
import com.tobfd.tsuzuki.feature.people.CharacterRoute
import com.tobfd.tsuzuki.feature.people.CharacterScreen
import com.tobfd.tsuzuki.feature.people.StaffRoute
import com.tobfd.tsuzuki.feature.people.StaffScreen
import com.tobfd.tsuzuki.feature.profile.ProfileRoute
import com.tobfd.tsuzuki.feature.profile.ProfileScreen
import com.tobfd.tsuzuki.feature.profile.UserRoute
import com.tobfd.tsuzuki.feature.profile.UserScreen
import com.tobfd.tsuzuki.feature.settings.SettingsRoute
import com.tobfd.tsuzuki.feature.settings.SettingsScreen
import com.tobfd.tsuzuki.navigation.LocalShellChrome
import com.tobfd.tsuzuki.navigation.ShellChrome
import com.tobfd.tsuzuki.navigation.TopLevelNavigator
import com.tobfd.tsuzuki.navigation.TopLevelTab

/**
 * The app shell for guests and logged-in users: [NavigationSuiteScaffold] (bottom bar on phones,
 * rail on wider windows) around a Navigation 3 [NavDisplay] with one back stack per tab.
 *
 * Back stacks and the selected tab survive rotation, resizing and process death. Each tab's entries
 * get their own saveable state and ViewModel stores, so switching tabs keeps everything.
 */
@Composable
fun AppShell(chrome: ShellChrome, onLogOut: () -> Unit, modifier: Modifier = Modifier) {
    val isGuest = chrome.viewer == null
    val navigator = rememberTopLevelNavigator()

    val entryProvider = entryProvider {
        entry<HomeRoute> {
            TabRoot(TopLevelTab.Home, navigator, onRenewLogin = onLogOut) { padding ->
                HomeScreen(
                    onOpenMedia = { navigator.navigate(MediaRoute(it)) },
                    onOpenUser = { navigator.navigate(UserRoute(it)) },
                    contentPadding = padding
                )
            }
        }
        entry<ListsRoute> {
            TabRoot(TopLevelTab.Lists, navigator, onRenewLogin = onLogOut) { padding ->
                ListsScreen(
                    isGuest = isGuest,
                    onLogIn = onLogOut,
                    onOpenMedia = { navigator.navigate(MediaRoute(it)) },
                    contentPadding = padding
                )
            }
        }
        entry<BrowseRoute> {
            TabRoot(TopLevelTab.Browse, navigator, onRenewLogin = onLogOut) { padding ->
                BrowseScreen(onOpenMedia = { navigator.navigate(MediaRoute(it)) }, contentPadding = padding)
            }
        }
        entry<ProfileRoute> {
            TabRoot(TopLevelTab.Profile, navigator, onRenewLogin = onLogOut) { padding ->
                ProfileScreen(
                    isGuest = isGuest,
                    onLogIn = onLogOut,
                    onOpenSettings = { navigator.navigate(SettingsRoute) },
                    onOpenUser = { navigator.navigate(UserRoute(it)) },
                    contentPadding = padding
                )
            }
        }
        entry<MediaRoute> { route ->
            MediaScreen(
                mediaId = route.id,
                onBack = { navigator.back() },
                onOpenCharacter = { navigator.navigate(CharacterRoute(it)) },
                onOpenStaff = { navigator.navigate(StaffRoute(it)) }
            )
        }
        entry<CharacterRoute> { route ->
            CharacterScreen(
                characterId = route.id,
                onBack = { navigator.back() },
                onOpenMedia = { navigator.navigate(MediaRoute(it)) },
                onOpenStaff = { navigator.navigate(StaffRoute(it)) }
            )
        }
        entry<StaffRoute> { route ->
            StaffScreen(
                staffId = route.id,
                onBack = { navigator.back() },
                onOpenCharacter = { navigator.navigate(CharacterRoute(it)) }
            )
        }
        entry<UserRoute> { route ->
            UserScreen(userName = route.name, onBack = { navigator.back() })
        }
        entry<NotificationsRoute> {
            NotificationsScreen(onBack = { navigator.back() }, onOpenMedia = { navigator.navigate(MediaRoute(it)) })
        }
        entry<SettingsRoute> {
            SettingsScreen(
                viewerName = LocalShellChrome.current.viewer?.name.orEmpty(),
                onBack = { navigator.back() },
                onLogOut = onLogOut
            )
        }
    }

    // Each tab decorates its own stack, so its saved state and ViewModels live as long as the stack
    // does, whether or not the tab is visible.
    val entriesPerTab: Map<TopLevelTab, List<NavEntry<NavKey>>> = TopLevelTab.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = navigator.stackOf(tab),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider
        )
    }

    CompositionLocalProvider(LocalShellChrome provides chrome) {
        NavigationSuiteScaffold(
            modifier = modifier,
            navigationSuiteItems = {
                TopLevelTab.entries.forEach { tab ->
                    val selected = tab == navigator.currentTab
                    item(
                        selected = selected,
                        onClick = { navigator.selectTab(tab) },
                        icon = {
                            Icon(
                                painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(tab.label)) }
                    )
                }
            }
        ) {
            NavDisplay(
                entries = navigator.visibleTabs.flatMap { entriesPerTab.getValue(it) },
                onBack = { navigator.back() }
            )
        }
    }
}

@Composable
private fun rememberTopLevelNavigator(): TopLevelNavigator {
    val selectedTab = rememberSaveable { mutableStateOf(TopLevelTab.Home) }
    val stacks = TopLevelTab.entries.associateWith { tab -> rememberNavBackStack(tab.root) }
    return remember(selectedTab, stacks) { TopLevelNavigator(selectedTab, stacks) }
}

/** A tab's root screen: the shell's top bar and banner around [content], plus the scroll-to-top request. */
@Composable
private fun TabRoot(
    tab: TopLevelTab,
    navigator: TopLevelNavigator,
    onRenewLogin: () -> Unit,
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    CompositionLocalProvider(LocalScrollToTopRequest provides navigator.scrollToTopRequests.getValue(tab)) {
        TabRootScaffold(
            title = stringResource(tab.label),
            onNotificationsClick = { navigator.navigate(NotificationsRoute) },
            onAvatarClick = { navigator.selectTab(TopLevelTab.Profile) },
            onRenewLogin = onRenewLogin,
            content = content
        )
    }
}
