plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "atlas.core.archive"
}

dependencies {
    implementation(libs.jsoup)
    implementation(libs.archive)
    implementation(libs.unifile)
}
