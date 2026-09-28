import com.tobfd.tsuzuki.buildlogic.configureKotlinJvm
import com.tobfd.tsuzuki.buildlogic.library
import com.tobfd.tsuzuki.buildlogic.libs
import com.tobfd.tsuzuki.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** `tsuzuki.jvm.library`: a pure Kotlin module without Android, such as `core/model`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            configureKotlinJvm()

            dependencies {
                "testImplementation"(libs.library("junit4"))
            }
        }
    }
}
