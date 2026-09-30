package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.settings.AniListOptionsChange
import com.tobfd.tsuzuki.core.data.settings.SettingsRepository
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.model.ViewerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository]. AniList changes apply [options] to [changes] unless [failure] is set;
 * set [gate] to hold a change until the test completes it.
 */
class FakeSettingsRepository(
    appearance: AppearanceSettings = AppearanceSettings(),
    var options: ViewerOptions = SampleData.viewer.options
) : SettingsRepository {
    override val appearance = MutableStateFlow(appearance)

    /** Every AniList change asked for, in order. */
    val changes = mutableListOf<AniListOptionsChange>()

    var failure: Throwable? = null

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun setColors(colors: AppColors) = appearance.update { it.copy(colors = colors) }

    override suspend fun setThemeMode(mode: AppThemeMode) = appearance.update { it.copy(themeMode = mode) }

    override suspend fun setPureBlack(enabled: Boolean) = appearance.update { it.copy(pureBlack = enabled) }

    override suspend fun updateAniListOptions(change: AniListOptionsChange): Result<ViewerOptions> {
        changes += change
        gate?.await()
        failure?.let { return Result.failure(it) }
        options = options.copy(
            titleLanguage = change.titleLanguage ?: options.titleLanguage,
            scoreFormat = change.scoreFormat ?: options.scoreFormat,
            displayAdultContent = change.displayAdultContent ?: options.displayAdultContent
        )
        return Result.success(options)
    }
}
