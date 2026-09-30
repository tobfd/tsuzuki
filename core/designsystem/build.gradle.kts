plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.android.compose)
}

android {
    namespace = "com.tobfd.tsuzuki.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    // Window size classes for layouts that switch on large screens (sheets as dialogs).
    implementation(libs.androidx.compose.material3.adaptive)
    api(libs.kotlinx.collections.immutable)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
