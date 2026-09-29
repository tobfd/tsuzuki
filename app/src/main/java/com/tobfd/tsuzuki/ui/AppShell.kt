package com.tobfd.tsuzuki.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.window.core.layout.WindowSizeClass
import com.tobfd.tsuzuki.core.designsystem.theme.BackSwipeEdge
import com.tobfd.tsuzuki.core.designsystem.theme.PredictiveBackEntry
import com.tobfd.tsuzuki.core.designsystem.theme.PredictiveBackState
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTransitions
import com.tobfd.tsuzuki.core.designsystem.theme.rememberPredictiveBackState
import com.tobfd.tsuzuki.core.designsystem.theme.rememberReducedMotion
import com.tobfd.tsuzuki.core.ui.LocalScrollToTopRequest
import com.tobfd.tsuzuki.feature.browse.BrowseRoute
import com.tobfd.tsuzuki.feature.browse.BrowseScreen
import com.tobfd.tsuzuki.feature.home.HomeRoute
import com.tobfd.tsuzuki.feature.home.HomeScreen
import com.tobfd.tsuzuki.feature.lists.ListsRoute
import com.tobfd.tsuzuki.feature.lists.editor.ListEditorRoute
import com.tobfd.tsuzuki.feature.lists.editor.ListEditorSheet
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
import com.tobfd.tsuzuki.navigation.BottomSheetSceneStrategy
import com.tobfd.tsuzuki.navigation.LocalShellChrome
import com.tobfd.tsuzuki.navigation.NavigationTransition
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
// The list-detail scene strategy (material3-adaptive-navigation3) is still experimental.
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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
                    onEditEntry = { navigator.navigate(ListEditorRoute(it)) },
                    onSeeAllLists = { navigator.selectTab(TopLevelTab.Lists) },
                    contentPadding = padding
                )
            }
        }
        entry<ListsRoute>(metadata = ListDetailSceneStrategy.listPane()) {
            ListsRoute(
                isGuest = isGuest,
                onLogIn = onLogOut,
                onOpenMedia = { navigator.navigate(MediaRoute(it)) },
                onEditEntry = { navigator.navigate(ListEditorRoute(it)) },
                onBrowse = { navigator.selectTab(TopLevelTab.Browse) }
            ) { actions, content ->
                TabRoot(TopLevelTab.Lists, navigator, onRenewLogin = onLogOut, actions = actions, content = content)
            }
        }
        entry<ListEditorRoute>(metadata = BottomSheetSceneStrategy.bottomSheet()) { route ->
            ListEditorSheet(
                mediaId = route.mediaId,
                onDismiss = { navigator.dismiss(route) },
                onOpenDetails = { mediaId ->
                    navigator.dismiss(route)
                    navigator.navigate(MediaRoute(mediaId))
                }
            )
        }
        entry<BrowseRoute>(metadata = ListDetailSceneStrategy.listPane()) {
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
        entry<MediaRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
            MediaScreen(
                mediaId = route.id,
                onBack = { navigator.back() },
                onOpenMedia = { navigator.navigate(MediaRoute(it)) },
                onOpenCharacter = { navigator.navigate(CharacterRoute(it)) },
                onOpenStaff = { navigator.navigate(StaffRoute(it)) },
                onEditEntry = { navigator.navigate(ListEditorRoute(it)) },
                onLogIn = onLogOut
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

    val reducedMotion = rememberReducedMotion()
    // Sheets over everything; on expanded widths Lists or Browse with the detail page beside them.
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val sceneStrategies = remember(listDetailStrategy) {
        listOf(BottomSheetSceneStrategy(), listDetailStrategy, SinglePaneSceneStrategy())
    }
    val predictiveBack = rememberPredictiveBackState()
    val navigationEventDispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    if (navigationEventDispatcher != null) {
        LaunchedEffect(navigationEventDispatcher, predictiveBack) {
            navigationEventDispatcher.transitionState.collect { gesture ->
                if (gesture is NavigationEventTransitionState.InProgress) {
                    if (gesture.direction == NavigationEventTransitionState.TRANSITIONING_BACK) {
                        predictiveBack.onGestureProgress(gesture.latestEvent)
                    }
                } else {
                    predictiveBack.onGestureEnded()
                }
            }
        }
    }
    val predictiveBackDecorator = remember(predictiveBack, reducedMotion) {
        NavEntryDecorator<NavKey> { entry ->
            PredictiveBackEntry(LocalNavAnimatedContentScope.current, predictiveBack, enabled = !reducedMotion) {
                entry.Content()
            }
        }
    }

    // Each tab decorates its own stack, so its saved state and ViewModels live as long as the stack
    // does, whether or not the tab is visible.
    val entriesPerTab: Map<TopLevelTab, List<NavEntry<NavKey>>> = TopLevelTab.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = navigator.stackOf(tab),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                predictiveBackDecorator
            ),
            entryProvider = entryProvider
        )
    }

    CompositionLocalProvider(LocalShellChrome provides chrome) {
        NavigationSuiteScaffold(
            modifier = modifier,
            layoutType = navigationSuiteType(),
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
            val slideDistancePx = with(LocalDensity.current) { TsuzukiTransitions.SharedAxisSlideDistance.roundToPx() }
            NavDisplay(
                entries = navigator.visibleTabs.flatMap { entriesPerTab.getValue(it) },
                sceneStrategies = sceneStrategies,
                // Tab switches fade through; opening screens, the back arrow and back without the
                // gesture use shared axis X.
                transitionSpec = {
                    if (navigator.lastTransition == NavigationTransition.TabSwitch) {
                        TsuzukiTransitions.fadeThrough()
                    } else {
                        TsuzukiTransitions.sharedAxisX(forward = true, slideDistancePx = slideDistancePx)
                    }
                },
                popTransitionSpec = {
                    if (navigator.lastTransition == NavigationTransition.TabSwitch) {
                        TsuzukiTransitions.fadeThrough()
                    } else {
                        TsuzukiTransitions.sharedAxisX(forward = false, slideDistancePx = slideDistancePx)
                    }
                },
                // Only the back gesture: the system's back animation, drawn by PredictiveBackEntry.
                predictivePopTransitionSpec = { TsuzukiTransitions.predictiveBack() },
                onBack = {
                    // Called before the dispatcher returns to idle, so a gesture is still in progress
                    // here when it was the gesture that went back.
                    val gesture = navigationEventDispatcher?.transitionState?.value
                    if (gesture is NavigationEventTransitionState.InProgress) {
                        predictiveBack.onGestureProgress(gesture.latestEvent)
                        predictiveBack.onGestureCommitted()
                    }
                    navigator.back()
                }
            )
        }
    }
}

/**
 * Bottom bar on compact widths, rail from medium width on (docs/ROADMAP.md, M3). Unlike the library
 * default this also uses the rail on phones in landscape, where a bottom bar would take a large part
 * of the low window height. Tabletop posture keeps the bar.
 */
@Composable
private fun navigationSuiteType(): NavigationSuiteType {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val wide = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    return if (wide && !adaptiveInfo.windowPosture.isTabletop) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }
}

private fun PredictiveBackState.onGestureProgress(event: NavigationEvent) {
    onGestureProgress(
        progress = event.progress,
        touchY = event.touchY,
        swipeEdge = when (event.swipeEdge) {
            NavigationEvent.EDGE_LEFT -> BackSwipeEdge.Left
            NavigationEvent.EDGE_RIGHT -> BackSwipeEdge.Right
            else -> BackSwipeEdge.None
        },
        // Before Android 16 the system gives no frame time.
        frameTimeMillis = event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
    )
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
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    CompositionLocalProvider(LocalScrollToTopRequest provides navigator.scrollToTopRequests.getValue(tab)) {
        TabRootScaffold(
            title = stringResource(tab.label),
            onNotificationsClick = { navigator.navigate(NotificationsRoute) },
            onAvatarClick = { navigator.selectTab(TopLevelTab.Profile) },
            onRenewLogin = onRenewLogin,
            actions = actions,
            content = content
        )
    }
}
