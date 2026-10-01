package com.tobfd.tsuzuki.core.common

/** Facts about the running build that the data layer needs; `app` provides them from its BuildConfig. */
data class AppBuildInfo(
    /** `versionName`, e.g. "1.0.0" (from the release tag) or "0.1.0" for local builds. */
    val versionName: String,
    val isDebug: Boolean,
    /** False in builds without the GitHub update check, e.g. a later Play Store build. */
    val updateCheckEnabled: Boolean
)
