plugins {
    // Version-less: the Kotlin Gradle plugin is already on the build classpath
    // (root buildscript), so a versioned alias is rejected by Gradle.
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation(projects.atlasCore)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.atlas.desktop.MainKt"
    }
}
