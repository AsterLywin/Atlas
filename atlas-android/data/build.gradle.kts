plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
}

android {
    namespace = "atlas.data"

    sqldelight {
        databases {
            create("Database") {
                packageName.set("atlas.data")
                dialect(libs.sqldelight.sqliteDialect338)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
                generateAsync.set(true)
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.atlasAndroid.sourceApi)
    implementation(projects.atlasAndroid.domain)
    implementation(projects.atlasAndroid.core.common)
    implementation(projects.atlasAndroid.core.metro)

    implementation(libs.metro.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.injekt)

    implementation(libs.kotlinx.datetime)

    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.bundles.sqldelight)
}
