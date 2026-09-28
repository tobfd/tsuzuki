plugins {
    alias(libs.plugins.tsuzuki.android.library)
    alias(libs.plugins.tsuzuki.hilt)
    alias(libs.plugins.apollo)
}

android {
    namespace = "com.tobfd.tsuzuki.core.network"
    buildFeatures {
        // BuildConfig.DEBUG gates rate-limit logging.
        buildConfig = true
    }
}

apollo {
    service("anilist") {
        packageName.set("com.tobfd.tsuzuki.core.network")
        // ./gradlew :core:network:downloadAnilistApolloSchemaFromIntrospection refreshes the schema.
        introspection {
            endpointUrl.set("https://graphql.anilist.co")
            schemaFile.set(file("src/main/graphql/com/tobfd/tsuzuki/core/network/schema.graphqls"))
        }
        plugin(libs.apollo.normalized.cache.compiler.plugin)
        pluginArgument("com.apollographql.cache.packageName", packageName.get())
    }
}

dependencies {
    api(project(":core:common"))
    api(libs.apollo.runtime)
    api(libs.apollo.normalized.cache)

    implementation(libs.apollo.normalized.cache.sqlite)
    implementation(libs.okhttp)
}
