package com.tobfd.tsuzuki.core.data.people

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.model.FavouriteKind
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.CharacterDetailQuery
import com.tobfd.tsuzuki.core.network.CharacterMediaQuery
import com.tobfd.tsuzuki.core.network.StaffDetailQuery
import com.tobfd.tsuzuki.core.network.ToggleFavouriteMutation
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun mediaJson(id: Int, adult: Boolean = false) = """
    {"__typename":"Media","id":$id,"type":"ANIME","format":"TV","status":"FINISHED","episodes":28,"chapters":null,
     "volumes":null,"isAdult":$adult,"averageScore":91,"seasonYear":2023,
     "startDate":{"__typename":"FuzzyDate","year":2023,"month":9,"day":29},
     "title":{"__typename":"MediaTitle","userPreferred":"Media $id","romaji":"Media $id","english":null,"native":null},
     "coverImage":{"__typename":"MediaCoverImage","large":null,"medium":null,"color":null},
     "nextAiringEpisode":null}
""".trimIndent()

private const val TANEZAKI =
    """{"__typename":"Staff","id":112215,"name":{"__typename":"StaffName","userPreferred":"Atsumi Tanezaki"},""" +
        """"image":{"__typename":"StaffImage","medium":null}}"""

private fun appearancesJson(ids: List<Int>, hasNextPage: Boolean, adultId: Int? = null) = """
    {"__typename":"MediaConnection","pageInfo":{"__typename":"PageInfo","hasNextPage":$hasNextPage},
     "edges":[${(ids + listOfNotNull(adultId)).joinToString(",") { id ->
    """{"__typename":"MediaEdge","id":$id,"characterRole":"MAIN","node":${mediaJson(id, adult = id == adultId)},
        "voiceActors":[$TANEZAKI]}"""
}}]}
""".trimIndent()

private val CHARACTER_JSON = """
    {"data":{"Character":{"__typename":"Character","id":176754,
     "name":{"__typename":"CharacterName","full":"Frieren","native":"フリーレン","alternative":["", "The Slayer"],"userPreferred":"Frieren"},
     "image":{"__typename":"CharacterImage","large":"https://img/frieren.jpg"},
     "description":"An elf mage. ~!She is over 1000 years old.!~","gender":"Female","age":"1000+","bloodType":null,
     "dateOfBirth":{"__typename":"FuzzyDate","year":null,"month":null,"day":null},
     "favourites":60000,"isFavourite":false,"siteUrl":"https://anilist.co/character/176754",
     "media":${appearancesJson(listOf(154587, 118586), hasNextPage = true, adultId = 999)}}}}
""".trimIndent()

private val STAFF_JSON = """
    {"data":{"Staff":{"__typename":"Staff","id":112215,
     "name":{"__typename":"StaffName","full":"Atsumi Tanezaki","native":"種﨑敦美","alternative":[],"userPreferred":"Atsumi Tanezaki"},
     "image":{"__typename":"StaffImage","large":null},"description":null,"primaryOccupations":["Voice Actor"],
     "gender":"Female","age":35,"dateOfBirth":{"__typename":"FuzzyDate","year":1988,"month":9,"day":27},
     "homeTown":"Oita, Japan","yearsActive":[2008],"favourites":20000,"isFavourite":true,"siteUrl":null,
     "characters":{"__typename":"CharacterConnection","pageInfo":{"__typename":"PageInfo","hasNextPage":false},
       "edges":[{"__typename":"CharacterEdge","id":1,"role":"MAIN",
         "node":{"__typename":"Character","id":176754,"name":{"__typename":"CharacterName","userPreferred":"Frieren"},"image":{"__typename":"CharacterImage","medium":null}},
         "media":[${mediaJson(154587)}]}]},
     "staffMedia":{"__typename":"MediaConnection","pageInfo":{"__typename":"PageInfo","hasNextPage":true},
       "edges":[{"__typename":"MediaEdge","id":2,"staffRole":"Theme Song Performance","node":${mediaJson(154587)}}]}}}}
""".trimIndent()

class DefaultPeopleRepositoryTest {

    private val apollo = TestApollo()
    private val clock = MutableClock(Instant.parse("2026-09-30T10:00:00Z"))
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val repository = DefaultPeopleRepository(apollo.client, session, clock)

    @After
    fun tearDown() = apollo.client.close()

    @Test
    fun character_isOneRequestAndLeavesOutAdultMedia() = runTest {
        apollo.enqueueJson(CharacterDetailQuery(id = 176754), CHARACTER_JSON)

        val character = repository.character(176754).getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals("Frieren", character.name.userPreferred)
        assertEquals("フリーレン", character.name.native)
        assertEquals(listOf("The Slayer"), character.name.alternative)
        assertEquals(null, character.dateOfBirth)
        assertEquals(listOf(154587, 118586), character.appearances.items.map { it.media.id })
        assertEquals("MAIN", character.appearances.items.first().role)
        assertEquals("Atsumi Tanezaki", character.appearances.items.first().voiceActor?.name)
        assertTrue(character.appearances.hasNextPage)
    }

    @Test
    fun character_reopenedWithinAnHour_makesNoRequest() = runTest {
        apollo.enqueueJson(CharacterDetailQuery(id = 176754), CHARACTER_JSON)
        repository.character(176754).getOrThrow()

        repository.character(176754).getOrThrow()
        assertEquals(1, apollo.requests)

        clock.advanceBy(Duration.ofMinutes(61))
        apollo.enqueueJson(CharacterDetailQuery(id = 176754), CHARACTER_JSON)
        repository.character(176754).getOrThrow()
        assertEquals(2, apollo.requests)
    }

    @Test
    fun character_offlineWithoutCache_fails() = runTest {
        apollo.enqueueOffline(CharacterDetailQuery(id = 1))
        assertEquals(AppError.Offline, repository.character(1).exceptionOrNull())
    }

    @Test
    fun moreAppearances_loadTheRequestedPage() = runTest {
        apollo.enqueueJson(
            CharacterMediaQuery(id = 176754, page = 2),
            """{"data":{"Character":{"__typename":"Character","id":176754,"media":${appearancesJson(
                listOf(7),
                false
            )}}}}"""
        )

        val page = repository.characterAppearances(176754, page = 2).getOrThrow()

        assertEquals(listOf(7), page.items.map { it.media.id })
        assertFalse(page.hasNextPage)
        assertEquals(2, (apollo.operations.single() as CharacterMediaQuery).page)
    }

    @Test
    fun staff_mapsCharactersVoicedAndProductionRoles() = runTest {
        apollo.enqueueJson(StaffDetailQuery(id = 112215), STAFF_JSON)

        val staff = repository.staff(112215).getOrThrow()

        assertEquals(1, apollo.requests)
        assertEquals(listOf("Voice Actor"), staff.occupations)
        assertEquals(FuzzyDate(1988, 9, 27), staff.dateOfBirth)
        assertEquals("Frieren", staff.characters.items.single().character.name)
        assertEquals(154587, staff.characters.items.single().media?.id)
        assertEquals("Theme Song Performance", staff.roles.items.single().role)
        assertTrue(staff.roles.hasNextPage)
        assertTrue(staff.isFavourite)
    }

    @Test
    fun toggleFavourite_asksAniListAgainOnTheNextOpen() = runTest {
        apollo.enqueueJson(CharacterDetailQuery(id = 176754), CHARACTER_JSON)
        repository.character(176754).getOrThrow()
        apollo.enqueueJson(
            ToggleFavouriteMutation(),
            """{"data":{"ToggleFavourite":{"__typename":"Favourites"}}}"""
        )

        repository.toggleFavourite(FavouriteKind.Character, 176754).getOrThrow()
        apollo.enqueueJson(CharacterDetailQuery(id = 176754), CHARACTER_JSON)
        repository.character(176754).getOrThrow()

        assertEquals(3, apollo.requests)
    }
}
