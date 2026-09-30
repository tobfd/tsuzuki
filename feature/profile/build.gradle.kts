plugins {
    alias(libs.plugins.tsuzuki.android.feature)
}

android {
    namespace = "com.tobfd.tsuzuki.feature.profile"
}

dependencies {
    implementation(libs.coil.compose)
}
