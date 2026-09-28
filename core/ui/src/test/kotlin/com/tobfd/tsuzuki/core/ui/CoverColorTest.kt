package com.tobfd.tsuzuki.core.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverColorTest {

    @Test
    fun aniListHexColor_parsesToOpaqueColor() {
        assertEquals(Color(0xFFBBF1A1), coverColorOrNull("#bbf1a1"))
    }

    @Test
    fun hexWithoutHash_parses() {
        assertEquals(Color(0xFF3DB4F2), coverColorOrNull("3DB4F2"))
    }

    @Test
    fun missingOrMalformedColor_returnsNull() {
        assertNull(coverColorOrNull(null))
        assertNull(coverColorOrNull(""))
        assertNull(coverColorOrNull("#bbf1a"))
        assertNull(coverColorOrNull("#zzzzzz"))
    }
}
