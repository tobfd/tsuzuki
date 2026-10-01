package com.tobfd.tsuzuki.core.data.update

import com.tobfd.tsuzuki.core.common.AppBuildInfo
import com.tobfd.tsuzuki.core.datastore.UpdateStore
import com.tobfd.tsuzuki.core.model.AppUpdate
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The update check against GitHub Releases (docs/RELEASING.md): it only tells, the download happens
 * in the browser. Off in builds that ship through a store ([isEnabled]).
 */
interface UpdateRepository {
    /** False in builds without the update check: no hint, no settings, no request. */
    val isEnabled: Boolean

    /** The setting "Check for updates automatically" (on by default). */
    val autoCheck: Flow<Boolean>

    suspend fun setAutoCheck(enabled: Boolean)

    /** A release newer than this build that the viewer hasn't closed yet, for the hint on Home. */
    val availableUpdate: Flow<AppUpdate?>

    /** The hint for [update] was closed or used; it doesn't come again for this version. */
    suspend fun dismiss(update: AppUpdate)

    /**
     * On app start: one request at most once a day, only with [autoCheck] on and never in debug builds.
     * Failures (offline, 404, rate limit) are ignored.
     */
    suspend fun checkOnStart()

    /** The manual check from Settings: the newer release, or null when this build is up to date. */
    suspend fun checkNow(): Result<AppUpdate?>
}

/** How often the automatic check may ask GitHub. */
internal val AUTO_CHECK_INTERVAL: Duration = Duration.ofDays(1)

internal class DefaultUpdateRepository @Inject constructor(
    private val releases: GitHubReleases,
    private val store: UpdateStore,
    private val buildInfo: AppBuildInfo,
    private val clock: Clock
) : UpdateRepository {

    private val installed: ReleaseVersion? = ReleaseVersion.parse(buildInfo.versionName)

    override val isEnabled: Boolean = buildInfo.updateCheckEnabled

    override val autoCheck: Flow<Boolean> = if (isEnabled) store.state.map { it.autoCheck } else flowOf(false)

    override suspend fun setAutoCheck(enabled: Boolean) = store.setAutoCheck(enabled)

    override val availableUpdate: Flow<AppUpdate?> = if (!isEnabled) {
        flowOf(null)
    } else {
        store.state.map { state ->
            val version = state.latestVersion ?: return@map null
            val url = state.latestUrl ?: return@map null
            AppUpdate(version, url).takeIf { version != state.dismissedVersion && isNewer(version) }
        }
    }

    override suspend fun dismiss(update: AppUpdate) = store.setDismissed(update.version)

    override suspend fun checkOnStart() {
        if (!isEnabled || buildInfo.isDebug) return
        val state = store.current()
        if (!state.autoCheck) return
        val last = state.lastCheck
        if (last != null && Duration.between(last, clock.instant()) < AUTO_CHECK_INTERVAL) return
        checkNow()
    }

    override suspend fun checkNow(): Result<AppUpdate?> {
        if (!isEnabled) return Result.success(null)
        val release = releases.latest().getOrElse { return Result.failure(it) }
        val version = release
            ?.takeUnless { it.draft || it.prerelease }
            ?.let { ReleaseVersion.parse(it.tagName) }
            ?.toString()
        val update = version?.takeIf(::isNewer)?.let { AppUpdate(it, release.htmlUrl) }
        store.setChecked(clock.instant(), update?.version, update?.url)
        return Result.success(update)
    }

    private fun isNewer(version: String): Boolean {
        val candidate = ReleaseVersion.parse(version) ?: return false
        // A build without a proper version (shouldn't happen) never nags.
        return installed != null && candidate > installed
    }
}
