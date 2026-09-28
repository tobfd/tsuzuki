plugins {
    alias(libs.plugins.tsuzuki.android.feature)
}

android {
    namespace = "com.tobfd.tsuzuki.feature.auth"
}

dependencies {
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
}
