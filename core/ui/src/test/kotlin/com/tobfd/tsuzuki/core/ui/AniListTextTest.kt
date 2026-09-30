package com.tobfd.tsuzuki.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListTextTest {

    @Test
    fun spoilers_areHiddenUntilRevealed() {
        val html = "Frieren meets <span class='markdown_spoiler'>Fern's teacher</span> and ~!a dragon!~."
        assertTrue(hasSpoilers(html))
        assertEquals(
            "Frieren meets <i>Spoiler</i> and <i>Spoiler</i>.",
            withSpoilers(html, reveal = false, placeholder = "Spoiler")
        )
        assertEquals(
            "Frieren meets Fern's teacher and a dragon.",
            withSpoilers(html, reveal = true, placeholder = "Spoiler")
        )
        assertFalse(hasSpoilers("No spoilers here."))
    }

    @Test
    fun images_becomeLinks() {
        assertEquals(
            // The space keeps images next to each other apart; HTML collapses the double space.
            "Hi <a href=\"https://i.imgur.com/x.png\">[Image]</a>  there",
            withImagesAsLinks("Hi <img width='220' src='https://i.imgur.com/x.png'> there", "[Image]")
        )
    }
}
