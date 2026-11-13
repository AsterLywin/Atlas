plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "atlas.core.metadata"
}

dependencies {
    implementation(projects.atlasAndroid.sourceApi)

    implementation(libs.bundles.serialization)
}
