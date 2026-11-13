package com.atlas.presentation.browse.components

import androidx.compose.runtime.Composable
import atlas.icons.materialsymbols.MaterialSymbols
import atlas.icons.materialsymbols.rounded.CollectionsBookmark
import atlas.presentation.core.components.Badge

@Composable
internal fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = MaterialSymbols.Rounded.CollectionsBookmark,
        )
    }
}
