package com.tobfd.tsuzuki.core.model

import java.time.Instant

/** The next episode of an anime the viewer is watching, for the "Next episode" widget. */
data class UpcomingEpisode(val entry: MediaListEntry, val episode: Int, val airingAt: Instant)

/** The last fetched activities of the people the viewer follows; [fetchedAt] is null before the first fetch. */
data class FriendActivityFeed(val activities: List<Activity>, val fetchedAt: Instant?)
