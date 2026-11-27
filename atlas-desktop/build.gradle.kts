import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The desktop version mirrors :atlas-android:app versionName
// (0.21.3, versionCode 39): the desktop versioning decision is
// still open, so no new scheme is invented here — the desktop ships
// the same number the Android app already carries.
val atlasDesktopVersion = "0.21.3"

plugins {
    // Version-less: the Kotlin Gradle plugin is already on the build classpath
    // (root buildscript), so a versioned alias is rejected by Gradle.
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    compilerOptions {
        // Same opt-ins the parsers module enables: the registry API and
        // the parser model classes are marked InternalParsersApi.
        freeCompilerArgs.addAll(
            "-opt-in=org.koitharu.kotatsu.parsers.InternalParsersApi",
            "-opt-in=org.koitharu.kotatsu.parsers.DirectParsersAccess",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.components.resources)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(projects.atlasCore)
                implementation(projects.atlasParsers)
                // The shared UI: library, browse, reader, settings.
                implementation(projects.atlasUi)
                // Coil 3 (JVM variant): covers + page images.
                implementation(libs.coil.core)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.okhttp)
                implementation(libs.kotlinx.coroutines.core)
                // The parser HTTP client doubles as Coil's network layer.
                implementation(libs.okhttp.core)
                // .tachibk backups: protobuf models + the okio stream format.
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.okio)
                // Desktop library database (JDBC SQLite driver).
                implementation(libs.sqldelight.sqliteDriver)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockwebserver)
            }
        }
    }
}

// SQLDelight: the desktop library database. Its schema lives under
// src/commonMain/sqldelight and is deliberately NOT the Android data
// module's schema — the desktop store is its own database.
sqldelight {
    databases {
        create("AtlasDatabase") {
            packageName.set("com.atlas.desktop.db")
            dialect(libs.sqldelight.sqliteDialect338)
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.atlas.desktop.MainKt"
        nativeDistributions {
            packageName = "Atlas"
            packageVersion = atlasDesktopVersion
            description = "Atlas is a manga / manhwa / manhua / webtoon / comic reader."
            vendor = "AsterLywin"
            copyright = "Copyright © 2026 AsterLywin"
            licenseFile.set(layout.projectDirectory.file("../LICENSE"))
            // macOS is deliberately absent: it needs an Apple developer
            // account, so no Dmg/Pkg target is configured.
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.AppImage,
            )
            windows {
                // The app's own icon (atlas-android fastlane icon.png),
                // converted to ICO (16/32/48/256 px) — same image,
                // only the container format differs.
                iconFile.set(project.file("packaging/atlas.ico"))
                shortcut = true
                menu = true
                menuGroup = "Atlas"
                perUserInstall = true
                // Stable upgrade id: an MSI upgrade replaces the
                // installed app instead of installing side by side.
                // Generated once (uuid4); do not change it.
                upgradeUuid = "bb73a77d-c398-4fa2-99d4-32fa4aaa64e2"
            }
            linux {
                // Same icon as the Windows one, in PNG form.
                iconFile.set(project.file("packaging/atlas.png"))
                packageName = "atlas"
                appCategory = "Graphics"
                debMaintainer = "AsterLywin"
                menuGroup = "Graphics"
                shortcut = true
            }
        }
    }
}

// Windows ZIP: Compose has no ZIP target format, so the Windows
// distributable (the runnable app dir from createDistributable) is
// zipped by this task. It only runs on Windows — zipping the Linux
// distributable and calling it "windows" would be a lie — so on any
// other OS it is skipped, and the Windows CI job runs it instead.
tasks.register<Zip>("packageWindowsZip") {
    group = "distribution"
    description = "Zips the Windows distributable as Atlas-<version>-windows.zip (Windows only)."
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("Atlas-$atlasDesktopVersion-windows.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/zip"))
    onlyIf { System.getProperty("os.name").lowercase().contains("win") }
}
