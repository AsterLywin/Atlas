plugins {
    // Version-less: the Kotlin Gradle plugin is already on the build classpath
    // (root buildscript), so a versioned alias is rejected by Gradle.
    alias(atlasx.plugins.kotlin.multiplatform)
    alias(atlasx.plugins.spotless)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    android {
        namespace = "com.atlas.ui"

        // Run commonTest on the Android host too (testDebugUnitTest),
        // like atlas-android:i18n does.
        withHostTest { }
    }

    // Android first (the atlas kotlin-multiplatform plugin configures the
    // android target); desktop is the second target of this module.
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                // Coil 3 (multiplatform): covers + reader pages. The
                // ImageLoader itself comes from the platform shell, built
                // on the parser host's own OkHttpClient.
                implementation(libs.coil.core)
                implementation(libs.coil.compose)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
