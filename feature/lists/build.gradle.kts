plugins {
    alias(libs.plugins.tsuzuki.android.feature)
}

android {
    namespace = "com.tobfd.tsuzuki.feature.lists"
}

dependencies {
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.test.runner)
}
