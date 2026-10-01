package com.tobfd.tsuzuki.core.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun tagsAndVersionNames_parseTheSame() {
        assertEquals(ReleaseVersion(1, 2, 3), ReleaseVersion.parse("v1.2.3"))
        assertEquals(ReleaseVersion(1, 2, 3), ReleaseVersion.parse("1.2.3"))
    }

    @Test
    fun brokenTags_areNoVersion() {
        listOf("", "v1", "v1.2", "1.2.3.4", "v1.2.3-beta", "release", "v1.x.3", "vv1.2.3").forEach {
            assertNull(it, ReleaseVersion.parse(it))
        }
    }

    @Test
    fun comparison_isNumeric_notTextual() {
        assertTrue(ReleaseVersion.parse("1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertTrue(ReleaseVersion.parse("2.0.0")!! > ReleaseVersion.parse("1.99.99")!!)
        assertTrue(ReleaseVersion.parse("1.0.1")!! > ReleaseVersion.parse("1.0.0")!!)
        assertEquals(0, ReleaseVersion.parse("v1.0.0")!!.compareTo(ReleaseVersion.parse("1.0.0")!!))
    }
}
