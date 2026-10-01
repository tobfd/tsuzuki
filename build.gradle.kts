// Top-level build file. Module setup lives in the convention plugins in build-logic.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // AGP compiles Kotlin itself (built-in Kotlin); this pins the Kotlin Gradle plugin version it uses.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // LF everywhere, matching .gitattributes (Windows batch files are not formatted).
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    val ktlintVersion = libs.versions.ktlint.get()

    // File trees that skip build output entirely: a plain glob walks into build directories while
    // other tasks of the same build (KSP of the Baseline Profile variants) are still writing there.
    fun sources(pattern: String) = fileTree(rootDir) {
        include(pattern)
        exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".idea/**")
    }
    kotlin {
        target(sources("**/*.kt"))
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target(sources("**/*.kts"))
        ktlint(ktlintVersion)
    }
    format("xml") {
        target(sources("**/*.xml"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
