package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import java.time.Instant

/** Sample data shaped like real AniList responses. */
object SampleData {
    val viewer = Viewer(
        id = 5_424_000,
        name = "tobfd",
        avatarUrl = null,
        options = ViewerOptions(
            titleLanguage = TitleLanguage.ROMAJI,
            staffNameLanguage = StaffNameLanguage.ROMAJI_WESTERN,
            displayAdultContent = false,
            scoreFormat = ScoreFormat.POINT_10_DECIMAL
        )
    )

    val tokenExpiry: Instant = Instant.parse("2027-09-28T12:00:00Z")
}
