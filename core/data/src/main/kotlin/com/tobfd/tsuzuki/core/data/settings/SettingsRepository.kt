package com.tobfd.tsuzuki.core.data.settings

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.apolloStore
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.mapper.enumNamed
import com.tobfd.tsuzuki.core.database.dao.MediaListDao
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.datastore.SettingsStore
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.ViewerOptions
import com.tobfd.tsuzuki.core.network.UpdateUserOptionsMutation
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import com.tobfd.tsuzuki.core.network.type.ScoreFormat as NetworkScoreFormat
import com.tobfd.tsuzuki.core.network.type.UserTitleLanguage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** One change to the viewer's AniList options; null fields stay as they are. */
data class AniListOptionsChange(
    val titleLanguage: TitleLanguage? = null,
    val scoreFormat: ScoreFormat? = null,
    val displayAdultContent: Boolean? = null
)

/** Settings (docs/ROADMAP.md, M11): the app's look on the device and the viewer's options on AniList. */
interface SettingsRepository {
    val appearance: Flow<AppearanceSettings>

    suspend fun setColors(colors: AppColors)

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setPureBlack(enabled: Boolean)

    /**
     * Saves [change] to AniList with one `UpdateUser` request and caches the options AniList returns. Every
     * screen then follows them: the Apollo cache is cleared (its titles and scores were in the old options) and
     * the titles in the offline lists are picked again without a sync. Fails as an `AppError`, changing nothing.
     */
    suspend fun updateAniListOptions(change: AniListOptionsChange): Result<ViewerOptions>
}

@Singleton
internal class DefaultSettingsRepository @Inject constructor(
    private val settingsStore: SettingsStore,
    private val sessionStore: SessionStore,
    private val apolloClient: ApolloClient,
    private val listDao: MediaListDao
) : SettingsRepository {

    override val appearance: Flow<AppearanceSettings> = settingsStore.appearance

    override suspend fun setColors(colors: AppColors) = settingsStore.setColors(colors)

    override suspend fun setThemeMode(mode: AppThemeMode) = settingsStore.setThemeMode(mode)

    override suspend fun setPureBlack(enabled: Boolean) = settingsStore.setPureBlack(enabled)

    override suspend fun updateAniListOptions(change: AniListOptionsChange): Result<ViewerOptions> {
        val current = sessionStore.session.first().viewer?.options
            ?: return Result.failure(AppError.Unauthorized)
        val mutation = UpdateUserOptionsMutation(
            titleLanguage = Optional.presentIfNotNull(
                change.titleLanguage?.let {
                    UserTitleLanguage.safeValueOf(it.name)
                }
            ),
            displayAdultContent = Optional.presentIfNotNull(change.displayAdultContent),
            scoreFormat = Optional.presentIfNotNull(change.scoreFormat?.let { NetworkScoreFormat.safeValueOf(it.name) })
        )
        val response = try {
            apolloClient.mutation(mutation).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        val user = response.data?.UpdateUser
            ?: return Result.failure(AppError.Unknown("AniList returned no user"))
        val options = current.copy(
            titleLanguage = enumNamed<TitleLanguage>(user.options?.titleLanguage?.rawValue) ?: current.titleLanguage,
            displayAdultContent = user.options?.displayAdultContent ?: current.displayAdultContent,
            scoreFormat = enumNamed<ScoreFormat>(user.mediaListOptions?.scoreFormat?.rawValue) ?: current.scoreFormat
        )
        sessionStore.saveViewerOptions(options)
        apolloClient.apolloStore.clearAll()
        if (options.titleLanguage != current.titleLanguage) {
            listDao.applyTitleLanguage(options.titleLanguage.name.removeSuffix("_STYLISED"))
        }
        return Result.success(options)
    }
}
