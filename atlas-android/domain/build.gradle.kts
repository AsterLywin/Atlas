plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

android {
    namespace = "atlas.domain"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(projects.atlasAndroid.sourceApi)
    implementation(projects.atlasAndroid.core.common)
    implementation(projects.atlasAndroid.core.metro)

    implementation(libs.metro.runtime)
    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.bundles.serialization)

    implementation(libs.kotlinx.datetime)

    implementation(libs.unifile)

    api(libs.androidx.paging.runtime)

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtimeAnnotation)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
