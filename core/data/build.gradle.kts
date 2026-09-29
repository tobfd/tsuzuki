plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tobfd.tsuzuki.core.data"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))

    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.apollo.testing.support)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
