package com.tobfd.tsuzuki.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.tobfd.tsuzuki.R
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.feature.browse.BrowseRoute
import com.tobfd.tsuzuki.feature.home.HomeRoute
import com.tobfd.tsuzuki.feature.lists.ListsRoute
import com.tobfd.tsuzuki.feature.profile.ProfileRoute

/** The four tabs (docs/PRODUCT.md, D4), each with its own back stack starting at [root]. */
enum class TopLevelTab(
    val root: NavKey,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int
) {
    Home(HomeRoute, R.string.tab_home, TsuzukiIcons.Home, TsuzukiIcons.HomeFilled),
    Lists(ListsRoute, R.string.tab_lists, TsuzukiIcons.List, TsuzukiIcons.List),
    Browse(BrowseRoute, R.string.tab_browse, TsuzukiIcons.Explore, TsuzukiIcons.ExploreFilled),
    Profile(ProfileRoute, R.string.tab_profile, TsuzukiIcons.Person, TsuzukiIcons.PersonFilled)
}
