plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.android.compose)
}

android {
    namespace = "com.tobfd.tsuzuki.core.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:model"))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
