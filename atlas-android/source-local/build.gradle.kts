plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.metro)
}

android {
    namespace = "atlas.source.local"
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(projects.atlasAndroid.sourceApi)
    implementation(projects.atlasAndroid.i18n)

    implementation(projects.atlasAndroid.core.archive)
    implementation(projects.atlasAndroid.core.common)
    implementation(projects.atlasAndroid.coreMetadata)
    implementation(projects.atlasAndroid.domain)

    implementation(libs.metro.runtime)

    implementation(libs.unifile)
    implementation(libs.bundles.serialization)

    implementation(libs.injekt)
    implementation(libs.jsoup)

    implementation(libs.kotlinx.datetime)
}
