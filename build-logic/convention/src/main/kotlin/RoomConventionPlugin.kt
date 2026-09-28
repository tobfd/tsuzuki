import androidx.room3.gradle.RoomExtension
import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import com.tobfd.tsuzuki.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `tsuzuki.room`: Room 3 with KSP; exported schemas go to `<module>/schemas`. */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("room"))
            pluginManager.apply(libs.pluginId("ksp"))

            extensions.configure<RoomExtension> {
                schemaDirectory(layout.projectDirectory.dir("schemas"))
            }

            dependencies {
                "implementation"(libs.library("androidx-room-runtime"))
                "ksp"(libs.library("androidx-room-compiler"))
                "testImplementation"(libs.library("androidx-room-testing"))
            }
        }
    }
}
