package com.atlas.reader.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import atlas.i18n.MR
import atlas.presentation.core.components.material.Scaffold
import atlas.presentation.core.i18n.stringResource
import atlas.presentation.core.screens.LoadingScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.atlas.presentation.components.AppBar
import com.atlas.presentation.more.stats.StatsScreenContent
import com.atlas.presentation.more.stats.StatsScreenState
import com.atlas.presentation.util.Screen
import dev.zacsweers.metrox.viewmodel.metroViewModel

class StatsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = metroViewModel<StatsViewModel>()
        val state by viewModel.state.collectAsState()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_stats),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            if (state is StatsScreenState.Loading) {
                LoadingScreen()
                return@Scaffold
            }

            StatsScreenContent(
                state = state as StatsScreenState.Success,
                paddingValues = paddingValues,
            )
        }
    }
}
