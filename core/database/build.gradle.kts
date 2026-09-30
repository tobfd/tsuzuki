plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.hilt)
    alias(libs.plugins.tsuzuki.room)
}

android {
    namespace = "com.tobfd.tsuzuki.core.database"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// MigrationTestHelper reads the exported schemas from the unit tests' assets.
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("schemas") }
    }
}

dependencies {
    // TsuzukiDatabase is a RoomDatabase; core/data runs transactions and clears it.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
