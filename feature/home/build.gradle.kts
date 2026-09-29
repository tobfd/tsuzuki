plugins {
    alias(libs.plugins.tsuzuki.android.feature)
}

android {
    namespace = "com.tobfd.tsuzuki.feature.home"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    androidTestImplementation(project(":core:testing"))
}
