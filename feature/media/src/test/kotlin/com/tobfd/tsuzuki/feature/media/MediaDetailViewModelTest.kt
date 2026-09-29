package com.tobfd.tsuzuki.feature.media

import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeMediaRepository
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

class MediaDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frieren = SampleData.frieren
    private val media = FakeMediaRepository(Result.success(FakeMediaRepository.detailOf(frieren.media)))
    private val lists = FakeListRepository()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))

    private fun viewModel() = MediaDetailViewModel(frieren.mediaId, media, lists, session)

    private fun TestScope.content(viewModel: MediaDetailViewModel): MediaDetailUiState.Content {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel.uiState.value as MediaDetailUiState.Content
    }

    @Test
    fun page_showsTheDetailAndTheEntryFromTheList() = runTest {
        lists.entries.value = listOf(frieren)
        val content = content(viewModel())
        assertEquals(frieren.media, content.detail.media)
        assertEquals(frieren, content.entry)
        assertEquals(SampleData.viewer, content.viewer)
    }

    @Test
    fun failure_showsTheErrorAndRetryLoadsAgain() = runTest {
        media.detail.value = Result.failure(AppError.Offline)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertEquals(MediaDetailUiState.Error(AppError.Offline), viewModel.uiState.value)

        viewModel.onRetry()
        runCurrent()
        assertEquals(2, media.observeCalls)
    }

    @Test
    fun listButton_onAListedMedia_opensTheEditor() = runTest {
        lists.entries.value = listOf(frieren)
        val viewModel = viewModel()
        content(viewModel)
        viewModel.eventFlow.test {
            viewModel.onListButton()
            assertEquals(MediaDetailEvent.OpenEditor(frieren.mediaId), awaitItem())
        }
    }

    @Test
    fun listButton_onANewMedia_addsItAsPlanningThenOpensTheEditor() = runTest {
        lists.addEntry = frieren
        val viewModel = viewModel()
        assertNull(content(viewModel).entry)
        viewModel.eventFlow.test {
            viewModel.onListButton()
            assertEquals(MediaDetailEvent.OpenEditor(frieren.mediaId), awaitItem())
        }
        assertEquals(listOf(frieren.mediaId), lists.added)
    }

    @Test
    fun addingOffline_saysWhy() = runTest {
        lists.addResult = Result.failure(AppError.Offline)
        val viewModel = viewModel()
        content(viewModel)
        viewModel.eventFlow.test {
            viewModel.onListButton()
            assertEquals(MediaDetailEvent.Failed(AppError.Offline), awaitItem())
        }
        assertFalse((viewModel.uiState.value as MediaDetailUiState.Content).adding)
    }

    @Test
    fun guests_areAskedToLogIn() = runTest {
        session.sessionState.value = SessionState.Guest
        val viewModel = viewModel()
        content(viewModel)
        viewModel.eventFlow.test {
            viewModel.onListButton()
            assertEquals(MediaDetailEvent.LogInToUse, awaitItem())
            viewModel.onToggleFavourite()
            assertEquals(MediaDetailEvent.LogInToUse, awaitItem())
        }
    }

    @Test
    fun favourite_isOptimisticAndGoesBackOnError() = runTest {
        val viewModel = viewModel()
        content(viewModel)
        viewModel.onToggleFavourite()
        runCurrent()
        assertTrue((viewModel.uiState.value as MediaDetailUiState.Content).isFavourite)

        media.favouriteResult = Result.failure(AppError.RateLimited(10))
        viewModel.eventFlow.test {
            viewModel.onToggleFavourite()
            assertEquals(MediaDetailEvent.Failed(AppError.RateLimited(10)), awaitItem())
        }
        assertTrue((viewModel.uiState.value as MediaDetailUiState.Content).isFavourite)
        assertEquals(listOf(frieren.mediaId, frieren.mediaId), media.favouriteToggles)
    }

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
}
