package com.tobfd.tsuzuki.core.model

/** The logged-in AniList user with the options the app respects everywhere. */
data class Viewer(val id: Int, val name: String, val avatarUrl: String?, val options: ViewerOptions)

/** The viewer's AniList settings; the app reads them and, from M11, writes them back. */
data class ViewerOptions(
    val titleLanguage: TitleLanguage,
    val staffNameLanguage: StaffNameLanguage,
    val displayAdultContent: Boolean,
    val scoreFormat: ScoreFormat
)

/** Mirrors AniList's `UserTitleLanguage`. */
enum class TitleLanguage {
    ROMAJI,
    ENGLISH,
    NATIVE,
    ROMAJI_STYLISED,
    ENGLISH_STYLISED,
    NATIVE_STYLISED
}

/** Mirrors AniList's `UserStaffNameLanguage`. */
enum class StaffNameLanguage {
    ROMAJI_WESTERN,
    ROMAJI,
    NATIVE
}
