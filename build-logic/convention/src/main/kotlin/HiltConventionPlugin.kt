import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import com.tobfd.tsuzuki.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** `tsuzuki.hilt`: Hilt with KSP for an Android module. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ksp"))
            pluginManager.apply(libs.pluginId("hilt"))

            dependencies {
                "implementation"(libs.library("hilt-android"))
                "ksp"(libs.library("hilt-compiler"))
            }
        }
    }
}
