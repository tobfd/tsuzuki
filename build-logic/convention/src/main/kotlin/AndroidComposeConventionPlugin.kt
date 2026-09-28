import com.android.build.api.dsl.CommonExtension
import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * `tsuzuki.android.compose`: enables Compose in an Android module. Apply after
 * `tsuzuki.android.application` or `tsuzuki.android.library`.
 *
 * Compose compiler metrics and reports: add `-Ptsuzuki.composeReports=true` to a build; they are
 * written to `build/compose-reports` of each module.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                val reportsDir = providers.gradleProperty("tsuzuki.composeReports")
                    .filter(String::toBoolean)
                    .flatMap { layout.buildDirectory.dir("compose-reports") }
                metricsDestination.set(reportsDir)
                reportsDestination.set(reportsDir)
            }

            dependencies {
                val bom = libs.library("androidx-compose-bom")
                "implementation"(platform(bom))
                "androidTestImplementation"(platform(bom))
                "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
                "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
            }
        }
    }
}
