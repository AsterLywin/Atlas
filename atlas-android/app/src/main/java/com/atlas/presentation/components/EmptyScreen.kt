package com.atlas.presentation.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import atlas.i18n.MR
import atlas.icons.materialsymbols.MaterialSymbols
import atlas.icons.materialsymbols.automirroredrounded.Help
import atlas.icons.materialsymbols.rounded.Refresh
import atlas.presentation.core.screens.EmptyScreen
import atlas.presentation.core.screens.EmptyScreenAction
import com.atlas.presentation.theme.TachiyomiPreviewTheme

@PreviewLightDark
@Composable
private fun NoActionPreview() {
    TachiyomiPreviewTheme {
        Surface {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun WithActionPreview() {
    TachiyomiPreviewTheme {
        Surface {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                actions = listOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = MaterialSymbols.Rounded.Refresh,
                        onClick = {},
                    ),
                    EmptyScreenAction(
                        stringRes = MR.strings.getting_started_guide,
                        icon = MaterialSymbols.AutoMirroredRounded.Help,
                        onClick = {},
                    ),
                ),
            )
        }
    }
}
