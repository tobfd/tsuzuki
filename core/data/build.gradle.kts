plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.hilt)
}

android {
    namespace = "com.tobfd.tsuzuki.core.data"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))

    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.json)
}
