package com.tobfd.tsuzuki.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The app under test. */
internal const val PACKAGE_NAME = "com.tobfd.tsuzuki"

private const val TIMEOUT_MS = 10_000L

/**
 * The journeys work without an account: a fresh install continues as a guest, so neither the profile
 * nor the benchmarks need Tobias's login. The texts are the English ones (the emulator's language).
 */
internal fun MacrobenchmarkScope.continueAsGuestIfAsked() {
    device.wait(Until.hasObject(By.text("Home").pkg(PACKAGE_NAME)), 3_000)
    device.findObject(By.text("Browse without an account"))?.let {
        it.click()
        device.wait(Until.hasObject(By.text("Home")), TIMEOUT_MS)
    }
}

/** Home: scrolls the trending row and the activity feed. */
internal fun MacrobenchmarkScope.scrollHome() {
    device.findObject(By.text("Home"))?.click()
    device.wait(Until.hasObject(By.scrollable(true).pkg(PACKAGE_NAME)), TIMEOUT_MS)
    device.waitForIdle()
    flingFirstScrollable()
}

/** Browse: the Trending results as a list, then the first result's detail page. */
internal fun MacrobenchmarkScope.browseAndOpenDetail() {
    device.findObject(By.text("Browse"))?.click()
    device.wait(Until.hasObject(By.text("Trending")), TIMEOUT_MS)
    device.findObject(By.text("Trending"))?.click()
    device.waitForIdle()
    flingFirstScrollable()
    // The cards arrive from the network; without waiting the detail page is often never opened.
    device.wait(Until.findObject(By.textContains("TV ·")), TIMEOUT_MS)?.click()
    device.wait(Until.hasObject(By.text("Overview").pkg(PACKAGE_NAME)), TIMEOUT_MS)
    device.waitForIdle()
    flingFirstScrollable()
    device.pressBack()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.flingFirstScrollable() {
    val list = device.wait(Until.findObject(By.scrollable(true).pkg(PACKAGE_NAME)), TIMEOUT_MS) ?: return
    // Keep the gestures away from the system's back and home edges.
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}
