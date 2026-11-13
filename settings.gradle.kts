pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("atlasx") {
            from(files("gradle/atlas.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Atlas"
include(":atlas-core")
include(":atlas-desktop")
include(":atlas-android:app")
include(":atlas-android:baseline-profile")
include(":atlas-android:core-metadata")
include(":atlas-android:core:archive")
include(":atlas-android:core:common")
include(":atlas-android:core:metro")
include(":atlas-android:data")
include(":atlas-android:domain")
include(":atlas-android:i18n")
include(":atlas-android:icons:custom-material-symbols")
include(":atlas-android:icons:material-symbols")
include(":atlas-android:icons:simple-icons")
include(":atlas-android:presentation-core")
include(":atlas-android:presentation-widget")
include(":atlas-android:source-api")
include(":atlas-android:source-local")
include(":atlas-android:telemetry")
