package com.tobfd.tsuzuki.core.ui

import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaType
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.w3c.dom.Element

class StatusWordingTest {

    /** String resources of this module for one values folder, e.g. "values-de". */
    private fun strings(valuesDir: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$valuesDir/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun mediaStatusLabels_neverReuseAListStatusLabel() {
        listOf("values", "values-de").forEach { valuesDir ->
            val strings = strings(valuesDir)
            val listLabels = strings.filterKeys { it.startsWith("ui_list_status_") }.values.toSet()
            val mediaLabels = strings.filterKeys { it.startsWith("ui_media_status_") }.values
            assertEquals(valuesDir, MediaStatus.entries.size, mediaLabels.size)
            mediaLabels.forEach { label ->
                assertFalse("$valuesDir: \"$label\" is also a list status", label in listLabels)
            }
        }
    }

    @Test
    fun currentCompletedAndRepeating_haveSeparateAnimeAndMangaLabels() {
        listOf(MediaListStatus.CURRENT, MediaListStatus.COMPLETED, MediaListStatus.REPEATING).forEach { status ->
            assertNotEquals(status.name, status.labelRes(MediaType.ANIME), status.labelRes(MediaType.MANGA))
        }
    }

    @Test
    fun planningPausedAndDropped_shareOneLabelForAnimeAndManga() {
        listOf(MediaListStatus.PLANNING, MediaListStatus.PAUSED, MediaListStatus.DROPPED).forEach { status ->
            assertEquals(status.name, status.labelRes(MediaType.ANIME), status.labelRes(MediaType.MANGA))
        }
    }
}
