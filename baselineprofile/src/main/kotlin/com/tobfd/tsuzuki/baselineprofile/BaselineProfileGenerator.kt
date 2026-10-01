package com.tobfd.tsuzuki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile (`app/src/release/generated/baselineProfiles`). Run
 * `./gradlew :app:generateBaselineProfile` with the API 36 emulator connected.
 *
 * - [startup] is the cold start up to the first Home frame. Only this goes into the startup profile
 *   (`startup-prof.txt`), which R8 uses to put the start classes first in the DEX files; a startup
 *   profile with more than the start in it helps the start less.
 * - [journeys] is the start plus Home, Browse and the detail page, the paths every session takes.
 *   It feeds the baseline profile (`baseline-prof.txt`), which ART compiles ahead of time.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        continueAsGuestIfAsked()
    }

    @Test
    fun journeys() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = false) {
        pressHome()
        startActivityAndWait()
        continueAsGuestIfAsked()
        scrollHome()
        browseAndOpenDetail()
    }
}
