plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.metro)
}

android {
    namespace = "atlas.core.metro"
}

dependencies {
    implementation(libs.metro.runtime)
}
