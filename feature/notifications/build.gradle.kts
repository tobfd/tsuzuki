plugins {
    alias(libs.plugins.tsuzuki.android.feature)
}

android {
    namespace = "com.tobfd.tsuzuki.feature.notifications"
}

// Also the Android notifications: new episodes from local alarms, new AniList notifications from a
// periodic WorkManager check (docs/ROADMAP.md, Android notifications).
dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.paging.testing)
}
