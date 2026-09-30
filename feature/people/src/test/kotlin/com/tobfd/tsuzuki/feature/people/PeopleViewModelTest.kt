package com.tobfd.tsuzuki.feature.people

import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.CharacterAppearance
import com.tobfd.tsuzuki.core.model.CharacterDetail
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.FavouriteKind
import com.tobfd.tsuzuki.core.model.PersonName
import com.tobfd.tsuzuki.core.model.ProductionRole
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.StaffDetail
import com.tobfd.tsuzuki.core.testing.FakePeopleRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PeopleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePeopleRepository()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))

    private fun appearance(id: Int) =
        CharacterAppearance(SampleData.listEntry(id = id).media, role = "MAIN", voiceActor = null)

    private val frieren = CharacterDetail(
        id = 176754,
        name = PersonName("Frieren", "Frieren", "フリーレン", emptyList()),
        imageUrl = null,
        descriptionHtml = null,
        gender = null,
        age = null,
        bloodType = null,
        dateOfBirth = null,
        favourites = 1,
        isFavourite = false,
        siteUrl = null,
        appearances = ContentPage(listOf(appearance(1), appearance(2)), hasNextPage = true)
    )

    private fun TestScope.characterViewModel() = CharacterViewModel(176754, repository, session).also {
        backgroundScope.launch { it.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun character_showsThePageAndItsFirstAppearances() = runTest {
        repository.characterResult = Result.success(frieren)
        val viewModel = characterViewModel()

        assertEquals(PersonUiState.Content(frieren, isFavourite = false), viewModel.uiState.value)
        assertEquals(listOf(1, 2), viewModel.appearances.value.items.map { it.media.id })
        assertEquals(2, viewModel.appearances.value.nextPage)
    }

    @Test
    fun character_whenLoadingFails_showsTheErrorAndRetries() = runTest {
        repository.characterResult = Result.failure(AppError.Offline)
        val viewModel = characterViewModel()
        assertEquals(PersonUiState.Error(AppError.Offline), viewModel.uiState.value)

        repository.characterResult = Result.success(frieren)
        viewModel.onRetry()
        runCurrent()
        assertTrue(viewModel.uiState.value is PersonUiState.Content)
    }

    @Test
    fun loadMore_appendsTheNextPageWithoutRepeats() = runTest {
        repository.characterResult = Result.success(frieren)
        repository.appearancePages[2] = Result.success(ContentPage(listOf(appearance(2), appearance(3)), false))
        val viewModel = characterViewModel()

        viewModel.onLoadMoreAppearances()
        runCurrent()

        assertEquals(listOf(1, 2, 3), viewModel.appearances.value.items.map { it.media.id })
        assertNull(viewModel.appearances.value.nextPage)
        assertFalse(viewModel.appearances.value.loading)
        // Nothing more to load: another tap sends nothing.
        viewModel.onLoadMoreAppearances()
        runCurrent()
        assertEquals(listOf(2), repository.requestedPages)
    }

    @Test
    fun loadMore_whenItFails_keepsTheListAndReports() = runTest {
        repository.characterResult = Result.success(frieren)
        repository.appearancePages[2] = Result.failure(AppError.RateLimited(30))
        val viewModel = characterViewModel()

        viewModel.eventFlow.test {
            viewModel.onLoadMoreAppearances()
            assertEquals(PersonEvent.Failed(AppError.RateLimited(30)), awaitItem())
        }
        assertEquals(2, viewModel.appearances.value.items.size)
        assertEquals(2, viewModel.appearances.value.nextPage)
    }

    @Test
    fun favourite_isOptimisticAndRollsBackOnFailure() = runTest {
        repository.characterResult = Result.success(frieren)
        repository.favouriteResult = Result.failure(AppError.Offline)
        val viewModel = characterViewModel()

        viewModel.eventFlow.test {
            viewModel.onToggleFavourite()
            assertEquals(PersonEvent.Failed(AppError.Offline), awaitItem())
        }
        assertFalse((viewModel.uiState.value as PersonUiState.Content).isFavourite)
        assertEquals(listOf(FavouriteKind.Character to 176754), repository.favouriteToggles)

        repository.favouriteResult = Result.success(Unit)
        viewModel.onToggleFavourite()
        runCurrent()
        assertTrue((viewModel.uiState.value as PersonUiState.Content).isFavourite)
    }

    @Test
    fun favourite_asGuest_asksToLogIn() = runTest {
        session.sessionState.value = SessionState.Guest
        repository.characterResult = Result.success(frieren)
        val viewModel = characterViewModel()

        viewModel.eventFlow.test {
            viewModel.onToggleFavourite()
            assertEquals(PersonEvent.LogInToUse, awaitItem())
        }
        assertTrue(repository.favouriteToggles.isEmpty())
    }

    @Test
    fun staff_pagesCharactersAndRolesSeparately() = runTest {
        repository.staffResult = Result.success(
            StaffDetail(
                id = 112215,
                name = PersonName("Atsumi Tanezaki", null, null, emptyList()),
                imageUrl = null,
                descriptionHtml = null,
                occupations = emptyList(),
                gender = null,
                age = null,
                dateOfBirth = null,
                homeTown = null,
                yearsActive = emptyList(),
                favourites = null,
                isFavourite = true,
                siteUrl = null,
                characters = ContentPage(emptyList(), hasNextPage = false),
                roles = ContentPage(listOf(ProductionRole(SampleData.frieren.media, "Theme Song")), hasNextPage = true)
            )
        )
        repository.rolePages[2] = Result.success(
            ContentPage(listOf(ProductionRole(SampleData.frieren.media, "Insert Song")), hasNextPage = false)
        )
        val viewModel = StaffViewModel(112215, repository, session)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertNull(viewModel.characters.value.nextPage)
        viewModel.onLoadMoreCharacters()
        viewModel.onLoadMoreRoles()
        runCurrent()

        assertEquals(listOf("Theme Song", "Insert Song"), viewModel.roles.value.items.map { it.role })
        assertEquals(listOf(2), repository.requestedPages)
    }
}
