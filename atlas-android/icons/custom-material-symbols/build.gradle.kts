plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.compose)

    alias(atlasx.plugins.spotless)

    alias(libs.plugins.valkyrie)
}

android {
    namespace = "atlas.icons.custommaterialsymbols"
}

spotless {
    format("svg") {
        target("src/**/*.svg")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

valkyrie {
    packageName = "atlas.icons.custommaterialsymbols"
    generateAtSync = true

    imageVector {
        suppressUnusedReceiverWarning = true
    }

    iconPack {
        name = "CustomMaterialSymbols"
        targetSourceSet = "main"

        nested {
            name = "Rounded"
            sourceFolder = "rounded"
            autoMirror = false
        }
    }
}

dependencies {
    api(libs.androidx.compose.ui)
}
