import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import com.tobfd.tsuzuki.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `tsuzuki.android.feature`: a module under `feature` with Compose, Hilt, ViewModels and
 * Navigation 3 route keys (`@Serializable`). Dependencies on the `core` modules are added here
 * once those modules exist.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("tsuzuki.android.library")
            pluginManager.apply("tsuzuki.android.compose")
            pluginManager.apply("tsuzuki.hilt")
            pluginManager.apply(libs.pluginId("kotlin-serialization"))

            dependencies {
                "implementation"(libs.library("androidx-compose-material3"))
                "implementation"(libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-navigation3-runtime"))
                "implementation"(libs.library("kotlinx-collections-immutable"))

                "androidTestImplementation"(libs.library("androidx-compose-ui-test-junit4"))
                "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
