package com.tobfd.tsuzuki.core.model

/** One page of a longer list; [hasNextPage] is the only reliable paging hint (docs/ANILIST_API.md). */
data class ContentPage<T>(val items: List<T>, val hasNextPage: Boolean)

/** A character's or staff member's names; [userPreferred] follows the viewer's name language. */
data class PersonName(val userPreferred: String, val full: String?, val native: String?, val alternative: List<String>)

/** Everything the character page shows (docs/DESIGN.md, Character / Staff), from one request. */
data class CharacterDetail(
    val id: Int,
    val name: PersonName,
    val imageUrl: String?,
    /** AniList's HTML subset, spoilers included. */
    val descriptionHtml: String?,
    val gender: String?,
    val age: String?,
    val bloodType: String?,
    val dateOfBirth: FuzzyDate?,
    val favourites: Int?,
    val isFavourite: Boolean,
    val siteUrl: String?,
    /** The first page of media the character appears in. */
    val appearances: ContentPage<CharacterAppearance>
)

/** A media the character is in, with their role (`MAIN`, `SUPPORTING`, `BACKGROUND`) and Japanese voice actor. */
data class CharacterAppearance(val media: MediaLite, val role: String?, val voiceActor: PersonLite?)

/** Everything the staff page shows, from one request. */
data class StaffDetail(
    val id: Int,
    val name: PersonName,
    val imageUrl: String?,
    val descriptionHtml: String?,
    val occupations: List<String>,
    val gender: String?,
    val age: Int?,
    val dateOfBirth: FuzzyDate?,
    val homeTown: String?,
    val yearsActive: List<Int>,
    val favourites: Int?,
    val isFavourite: Boolean,
    val siteUrl: String?,
    /** First page of characters voiced. */
    val characters: ContentPage<VoicedCharacter>,
    /** First page of production roles ("Director", "Original Creator"). */
    val roles: ContentPage<ProductionRole>
)

/** A character the staff member voiced, with the media it is from (the first AniList lists). */
data class VoicedCharacter(val character: PersonLite, val role: String?, val media: MediaLite?)

/** A production role, e.g. "Director" on a media. */
data class ProductionRole(val media: MediaLite, val role: String?)

/** What a favourite heart belongs to. */
enum class FavouriteKind {
    Character,
    Staff
}
