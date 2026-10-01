package com.tobfd.tsuzuki.feature.widgets.friends

import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.FriendActivityFeed
import com.tobfd.tsuzuki.feature.widgets.WIDGET_MAX_ITEMS
import java.time.Instant

internal sealed interface FriendActivityState {
    data object LoggedOut : FriendActivityState

    /** [fetchedAt] is null until the first update arrived. */
    data class Ready(val activities: List<Activity>, val fetchedAt: Instant?) : FriendActivityState
}

internal fun friendActivityState(loggedIn: Boolean, feed: FriendActivityFeed): FriendActivityState = if (loggedIn) {
    FriendActivityState.Ready(feed.activities.take(WIDGET_MAX_ITEMS), feed.fetchedAt)
} else {
    FriendActivityState.LoggedOut
}

/** The cover of a list update's media; status posts have none. */
internal val Activity.coverUrl: String?
    get() = (this as? Activity.ListUpdate)?.media?.coverUrl
