import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import com.tobfd.tsuzuki.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `tsuzuki.hilt`: Hilt with KSP. Android modules get the Hilt Gradle plugin and `hilt-android`;
 * pure Kotlin modules (`tsuzuki.jvm.library`) get `hilt-core` for modules and injection.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ksp"))
            dependencies {
                "ksp"(libs.library("hilt-compiler"))
            }

            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply(libs.pluginId("hilt"))
                dependencies {
                    "implementation"(libs.library("hilt-android"))
                }
            }
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    "implementation"(libs.library("hilt-core"))
                }
            }
        }
    }
}
