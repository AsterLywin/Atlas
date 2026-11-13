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
        // Same opt-ins the upstream parsers build enables (its build.gradle.kts).
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=org.koitharu.kotatsu.parsers.InternalParsersApi",
            "-opt-in=org.koitharu.kotatsu.parsers.DirectParsersAccess",
        )
    }
    sourceSets {
        // The vendored parsers tree keeps its upstream layout: the stable
        // host<->parser contract lives under src/contract, the implementations
        // (plus the generated registry) under src/main. One compilation, so
        // `internal` is shared between them exactly as upstream.
        named("main") {
            kotlin.srcDir("src/contract/kotlin")
        }
    }
}

dependencies {
    implementation(projects.atlasCore)

    // What the vendored Kotatsu parsers need (versions from the Atlas catalog;
    // upstream pins older ones, Gradle resolves to these newer ones).
    implementation(libs.okhttp.core)
    implementation(libs.okio)
    implementation(libs.jsoup)
    // Jsoup's API carries org.jspecify @Nullable on inferred types (e.g.
    // `selectFirst(...)`); the annotation class must be on the compile
    // classpath or kotlinc fails with MISSING_DEPENDENCY_IN_INFERRED_TYPE.
    // It only arrives transitively in a runtime scope, so declare it.
    implementation(libs.jspecify)
    implementation(libs.json)
    implementation(libs.androidx.collection)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
