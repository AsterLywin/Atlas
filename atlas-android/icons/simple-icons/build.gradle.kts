plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.compose)

    alias(atlasx.plugins.spotless)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "atlas.icons.simpleicons"
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "atlas.icons.simpleicons"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "SimpleIcons"
        targetSourceSet = "main"
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
