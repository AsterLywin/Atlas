plugins {
    // Version-less: the Kotlin Gradle plugin is already on the build classpath
    // (root buildscript), so a versioned alias is rejected by Gradle.
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
