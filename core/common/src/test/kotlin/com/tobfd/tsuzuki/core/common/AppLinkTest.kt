package com.tobfd.tsuzuki.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinkTest {

    @Test
    fun everyDestination_survivesTheRoundTrip() {
        val destinations = listOf(
            AppDestination.Tab(AppTab.Home),
            AppDestination.Tab(AppTab.Lists),
            AppDestination.Media(154587, AppTab.Lists),
            AppDestination.Media(1, AppTab.Home),
            AppDestination.User(5424000, "tobfd"),
            AppDestination.User(7, "Gecko TV & Co/?")
        )
        destinations.forEach { assertEquals(it, AppLink.parse(AppLink.uri(it))) }
    }

    @Test
    fun media_isAPlainLink() {
        assertEquals("tsuzuki://open/media/154587?tab=lists", AppLink.uri(AppDestination.Media(154587, AppTab.Lists)))
    }

    @Test
    fun otherLinks_areNotDestinations() {
        assertNull(AppLink.parse("tsuzuki://auth#access_token=abc"))
        assertNull(AppLink.parse("https://anilist.co/anime/154587"))
        assertNull(AppLink.parse("tsuzuki://open/media/abc"))
        assertNull(AppLink.parse("tsuzuki://open/user/5"))
        assertNull(AppLink.parse("tsuzuki://open/tab/browse"))
        assertNull(AppLink.parse("not a uri"))
    }
}
