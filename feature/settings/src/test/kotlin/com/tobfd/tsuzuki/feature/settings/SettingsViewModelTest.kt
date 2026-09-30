package com.tobfd.tsuzuki.feature.settings

import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.settings.AniListOptionsChange
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.FakeSettingsRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import kotlinx.coroutines.CompletableDeferred
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

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))

    private fun TestScope.viewModel(): SettingsViewModel = SettingsViewModel(settings, session).also { viewModel ->
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun appearanceChanges_areStoredRightAway() = runTest {
        val viewModel = viewModel()

        viewModel.onColorsChange(AppColors.AniListBlue)
        viewModel.onThemeModeChange(AppThemeMode.Dark)
        viewModel.onPureBlackChange(true)
        runCurrent()

        assertEquals(
            AppearanceSettings(AppColors.AniListBlue, AppThemeMode.Dark, pureBlack = true),
            viewModel.uiState.value.appearance
        )
    }

    @Test
    fun guest_hasNoAniListOptions() = runTest {
        session.sessionState.value = SessionState.Guest
        val viewModel = viewModel()

        assertNull(viewModel.uiState.value.viewer)
        assertNull(viewModel.uiState.value.options)
    }

    @Test
    fun titleLanguage_showsAtOnceWhileSavingAndSendsOneChange() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.gate = gate
        val viewModel = viewModel()

        viewModel.onTitleLanguageChange(TitleLanguage.ENGLISH)
        runCurrent()

        assertEquals(TitleLanguage.ENGLISH, viewModel.uiState.value.options?.titleLanguage)
        assertTrue(viewModel.uiState.value.savingOptions)
        // A second change waits for the first.
        viewModel.onScoreFormatChange(ScoreFormat.POINT_5)
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(AniListOptionsChange(titleLanguage = TitleLanguage.ENGLISH)), settings.changes)
        assertFalse(viewModel.uiState.value.savingOptions)
    }

    @Test
    fun sameValue_sendsNothing() = runTest {
        val viewModel = viewModel()

        viewModel.onScoreFormatChange(SampleData.viewer.options.scoreFormat)
        runCurrent()

        assertTrue(settings.changes.isEmpty())
    }

    @Test
    fun failedSave_goesBackAndReportsTheError() = runTest {
        settings.failure = AppError.Offline
        val viewModel = viewModel()

        viewModel.eventFlow.test {
            viewModel.onAdultContentChange(true)
            assertEquals(SettingsEvent.SaveFailed(AppError.Offline), awaitItem())
        }
        runCurrent()

        assertFalse(viewModel.uiState.value.options!!.displayAdultContent)
        assertFalse(viewModel.uiState.value.savingOptions)
    }
}
