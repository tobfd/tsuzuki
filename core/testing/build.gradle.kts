plugins {
    alias(libs.plugins.tsuzuki.android.library)
}

android {
    namespace = "com.tobfd.tsuzuki.core.testing"
}

dependencies {
    api(project(":core:data"))
    api(libs.androidx.datastore)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
