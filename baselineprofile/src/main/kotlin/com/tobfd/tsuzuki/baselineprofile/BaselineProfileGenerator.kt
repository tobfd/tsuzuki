package com.tobfd.tsuzuki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects the Baseline Profile (`app/src/release/generated/baselineProfiles`): app start, Home,
 * Browse and the detail page, the paths every session takes. Run `./gradlew :app:generateBaselineProfile`
 * with the API 36 emulator or a phone connected.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        continueAsGuestIfAsked()
        scrollHome()
        browseAndOpenDetail()
    }
}
