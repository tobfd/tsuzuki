package com.tobfd.tsuzuki.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    optInForUnitTests()
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
    optInForUnitTests()
}

/**
 * Unit tests use experimental test APIs on purpose (`advanceUntilIdle`, `runCurrent`, Apollo's
 * `QueueTestNetworkTransport`), so their compilations opt in instead of warning on every call.
 * A marker is only added where the module can see it (Android modules always have coroutines;
 * Kotlin/JVM modules and Apollo only when they declare them), otherwise Kotlin would warn that the
 * marker is unresolved.
 */
private fun Project.optInForUnitTests() {
    tasks.withType(KotlinCompile::class.java).configureEach {
        val unitTest = name.endsWith("UnitTestKotlin") || name == "compileTestKotlin"
        if (unitTest) {
            val declared = listOf("api", "implementation", "testImplementation")
                .mapNotNull { configurations.findByName(it) }
                .flatMap { it.dependencies }
            val android = extensions.findByType(KotlinAndroidProjectExtension::class.java) != null
            if (android ||
                declared.any { it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines") }
            ) {
                compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
            if (declared.any { it.group == "com.apollographql.apollo" }) {
                compilerOptions.optIn.add("com.apollographql.apollo.annotations.ApolloExperimental")
            }
        }
    }
}
