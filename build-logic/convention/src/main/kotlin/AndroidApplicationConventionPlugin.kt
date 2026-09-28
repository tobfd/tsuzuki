import com.android.build.api.dsl.ApplicationExtension
import com.tobfd.tsuzuki.buildlogic.TsuzukiSdk
import com.tobfd.tsuzuki.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `tsuzuki.android.application`: the `app` module. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TsuzukiSdk.TARGET
                // Lint the app together with all modules it depends on.
                lint.checkDependencies = true
            }
        }
    }
}
