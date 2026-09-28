package com.tobfd.tsuzuki.core.data.mapper

import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import com.tobfd.tsuzuki.core.network.ViewerQuery

internal fun ViewerQuery.Viewer.toViewer(): Viewer = Viewer(
    id = id,
    name = name,
    avatarUrl = avatar?.large ?: avatar?.medium,
    options = ViewerOptions(
        titleLanguage = options?.titleLanguage?.rawValue.toEnum(TitleLanguage.ROMAJI),
        staffNameLanguage = options?.staffNameLanguage?.rawValue.toEnum(StaffNameLanguage.ROMAJI_WESTERN),
        displayAdultContent = options?.displayAdultContent ?: false,
        scoreFormat = mediaListOptions?.scoreFormat?.rawValue.toEnum(ScoreFormat.POINT_100)
    )
)

/** GraphQL enums carry AniList's raw names; unknown future values fall back to [default]. */
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default
