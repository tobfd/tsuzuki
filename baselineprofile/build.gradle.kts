plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Generates the app's Baseline Profile and runs the startup and scroll benchmarks (M12). Run on the
// API 36 emulator or a phone: ./gradlew :app:generateBaselineProfile
android {
    namespace = "com.tobfd.tsuzuki.baselineprofile"
    compileSdk { version = release(37) }

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The emulator is fine for collecting the profile; its benchmark numbers are only a rough guide.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
