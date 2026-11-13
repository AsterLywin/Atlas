plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.compose)

    alias(atlasx.plugins.spotless)
    alias(libs.plugins.metro)
}

android {
    namespace = "atlas.presentation.widget"
}

dependencies {
    implementation(projects.atlasAndroid.core.common)
    implementation(projects.atlasAndroid.core.metro)
    implementation(projects.atlasAndroid.domain)
    implementation(projects.atlasAndroid.presentationCore)
    implementation(projects.atlasAndroid.i18n)

    implementation(libs.androidx.glance.appWidget)
    implementation(libs.material)

    implementation(libs.kotlinx.datetime)

    implementation(libs.coil.core)

    implementation(libs.metro.runtime)
}
