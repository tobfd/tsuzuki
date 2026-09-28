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

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
