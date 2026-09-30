plugins {
    alias(libs.plugins.tsuzuki.android.feature)
}

android {
    namespace = "com.tobfd.tsuzuki.feature.browse"
}

dependencies {
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.androidx.paging.testing)
}
