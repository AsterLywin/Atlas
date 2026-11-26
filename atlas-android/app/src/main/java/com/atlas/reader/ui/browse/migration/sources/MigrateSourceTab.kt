package com.atlas.reader.ui.browse.migration.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import atlas.i18n.MR
import atlas.icons.materialsymbols.MaterialSymbols
import atlas.icons.materialsymbols.automirroredrounded.Help
import atlas.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.atlas.presentation.browse.MigrateSourceScreen
import com.atlas.presentation.components.AppBar
import com.atlas.presentation.components.TabContent
import com.atlas.reader.ui.browse.migration.manga.MigrateMangaScreen
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun Screen.migrateSourceTab(): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<MigrateSourceViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = MR.strings.label_migration,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.migration_help_guide),
                icon = MaterialSymbols.AutoMirroredRounded.Help,
                onClick = {
                    uriHandler.openUri("https://atlas.app/docs/guides/source-migration")
                },
            ),
        ),
        content = { contentPadding, _ ->
            MigrateSourceScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrateMangaScreen(source.id))
                },
                onToggleSortingDirection = viewModel::toggleSortingDirection,
                onToggleSortingMode = viewModel::toggleSortingMode,
            )
        },
    )
}
