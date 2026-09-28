plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.hilt)
}

android {
    namespace = "com.tobfd.tsuzuki.core.datastore"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
}
