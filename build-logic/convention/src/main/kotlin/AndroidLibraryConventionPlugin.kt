import com.android.build.api.dsl.LibraryExtension
import com.tobfd.tsuzuki.buildlogic.configureKotlinAndroid
import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import com.tobfd.tsuzuki.buildlogic.tsuzukiSdk
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `tsuzuki.android.library`: every Android module under `core` and `feature`. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = tsuzukiSdk.target
                lint.targetSdk = tsuzukiSdk.target
            }

            dependencies {
                "testImplementation"(libs.library("junit4"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
                "testImplementation"(libs.library("turbine"))
            }
        }
    }
}
