package com.atlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.atlas.ui.model.AppTheme
import com.atlas.ui.strings.AtlasStrings

/** The resolved strings for the current settings. */
val LocalAtlasStrings = compositionLocalOf<AtlasStrings> { AtlasStrings.english }

/** Resolve the strings for [language] against the device locale. */
fun resolveStrings(language: com.atlas.ui.model.AppLanguage, systemLanguage: String): AtlasStrings =
    AtlasStrings.forLanguage(language, systemLanguage.startsWith("fa"))

/**
 * The shared theme: Material3 light/dark by [theme] (System follows
 * the device), plus the resolved [AtlasStrings] for the subtree.
 */
@Composable
fun AtlasTheme(
    theme: AppTheme,
    strings: AtlasStrings,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
    ) {
        CompositionLocalProvider(LocalAtlasStrings provides strings) {
            content()
        }
    }
}
