// Top-level build file. Module setup lives in the convention plugins in build-logic.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
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
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint(ktlintVersion)
    }
    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", ".idea/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
