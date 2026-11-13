plugins {
    alias(atlasx.plugins.android.library)
    alias(atlasx.plugins.compose)

    alias(atlasx.plugins.spotless)
}

android {
    namespace = "atlas.presentation.core"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

dependencies {
    api(projects.atlasAndroid.core.common)
    api(projects.atlasAndroid.i18n)
    implementation(projects.atlasAndroid.icons.materialSymbols)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3NavSuite)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationGraphics)
    debugImplementation(libs.androidx.compose.uiTooling)
    implementation(libs.androidx.compose.uiToolingPreview)
    implementation(libs.androidx.compose.uiUtil)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
}
