import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.tobfd.tsuzuki.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.tsuzuki.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.tsuzuki.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.tsuzuki.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.tsuzuki.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.tsuzuki.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = libs.plugins.tsuzuki.room.get().pluginId
            implementationClass = "RoomConventionPlugin"
        }
    }
}
