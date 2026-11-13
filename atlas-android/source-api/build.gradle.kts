plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.atlas.reader.source"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    implementation(projects.atlasAndroid.core.common)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.injekt)
    implementation(libs.rxJava)
    implementation(libs.jsoup)

    implementation(libs.androidx.preference)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
