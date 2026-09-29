import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.tsuzuki.android.application)
    alias(libs.plugins.tsuzuki.android.compose)
    alias(libs.plugins.tsuzuki.hilt)
}

android {
    namespace = "com.tobfd.tsuzuki"

    defaultConfig {
        applicationId = "com.tobfd.tsuzuki"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

// The AniList client ID comes from local.properties (never committed) and reaches the code as
// BuildConfig.ANILIST_CLIENT_ID. It is resolved lazily, so only tasks that generate BuildConfig
// fail when it is missing; everything else (Spotless, IDE sync) keeps working.
val anilistClientId: Provider<String> = providers
    .fileContents(layout.settingsDirectory.file("local.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) }.getProperty("anilist.clientId").orEmpty().trim() }
    .orElse("")

androidComponents {
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "ANILIST_CLIENT_ID",
            anilistClientId.map { clientId ->
                if (clientId.toLongOrNull() == null) {
                    throw GradleException(
                        "AniList client ID missing or invalid. Add `anilist.clientId=<numeric client ID>` to " +
                            "local.properties in the project root. Create the client at " +
                            "https://anilist.co/settings/developer with redirect URL tsuzuki://auth (see README.md)."
                    )
                }
                BuildConfigField("String", "\"$clientId\"", "AniList API client ID from local.properties")
            }
        )
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:home"))
    implementation(project(":feature:lists"))
    implementation(project(":feature:media"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:people"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auth"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.navigation.suite)

    testImplementation(project(":core:testing"))
}
