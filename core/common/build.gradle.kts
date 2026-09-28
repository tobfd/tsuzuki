plugins {
    alias(libs.plugins.tsuzuki.jvm.library)
    alias(libs.plugins.tsuzuki.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
