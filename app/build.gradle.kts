import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.tsuzuki.android.application)
    alias(libs.plugins.tsuzuki.android.compose)
    alias(libs.plugins.tsuzuki.hilt)
    alias(libs.plugins.baselineprofile)
}

// Release builds get their version from the Git tag (.github/workflows/release.yml passes
// -Ptsuzuki.versionName=1.2.3 -Ptsuzuki.versionCode=10203); local builds keep the defaults.
val releaseVersionName: String? = providers.gradleProperty("tsuzuki.versionName").orNull
val releaseVersionCode: Int? = providers.gradleProperty("tsuzuki.versionCode").orNull?.toInt()

// The GitHub update check (docs/RELEASING.md) is on unless a build turns it off, e.g. a later Play Store
// build with -Ptsuzuki.updateCheck=false.
val updateCheck: Boolean = providers.gradleProperty("tsuzuki.updateCheck").orNull?.toBooleanStrict() ?: true

// Signing for releases comes only from environment variables that the release workflow fills from
// GitHub secrets. Without them (local builds, CI) the release build is unsigned.
val releaseKeystore: String? = providers.environmentVariable("TSUZUKI_KEYSTORE_FILE").orNull

android {
    namespace = "com.tobfd.tsuzuki"

    defaultConfig {
        applicationId = "com.tobfd.tsuzuki"
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersionName ?: "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("TSUZUKI_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("TSUZUKI_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("TSUZUKI_KEY_PASSWORD").get()
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 in full mode (AGP's default): code and resource shrinking, obfuscation, optimization.
            // The app's own keep rules live in src/main/keepRules, which AGP picks up by itself.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
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
    // The Baseline Profile plugin collects from this copy of release. It has to stay unobfuscated so the
    // profile names the real classes; R8 maps the profile onto the minified release build itself.
    beforeVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.isMinifyEnabled = false
        variant.shrinkResources = false
    }
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "UPDATE_CHECK",
            BuildConfigField("boolean", updateCheck.toString(), "Whether the app checks GitHub Releases for updates")
        )
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
    implementation(project(":feature:widgets"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Installs the Baseline Profile on devices without Play's cloud profiles (sideloads, first start).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.navigation.suite)

    // The debug-only widget sample data writes straight into the app's storage.
    debugImplementation(project(":core:database"))
    debugImplementation(project(":core:datastore"))

    testImplementation(project(":core:testing"))
}
