package com.tobfd.tsuzuki.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** SDK levels shared by every Android module (see docs/PRODUCT.md, D6). */
internal object TsuzukiSdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 31
}

private val javaVersion = JavaVersion.VERSION_17

/**
 * Base Android + Kotlin setup shared by application and library modules. Kotlin compilation is
 * provided by AGP's built-in Kotlin support, so no Kotlin Android plugin is applied.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk { version = release(TsuzukiSdk.COMPILE) }
        defaultConfig.minSdk = TsuzukiSdk.MIN
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        compileOptions.sourceCompatibility = javaVersion
        compileOptions.targetCompatibility = javaVersion

        lint.abortOnError = true
        lint.warningsAsErrors = false
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

/** Kotlin/JVM setup for pure Kotlin modules such as `core/model`. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}
