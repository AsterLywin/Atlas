package com.atlas.presentation.more.settings.screen.debug

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import atlas.i18n.MR
import atlas.icons.materialsymbols.MaterialSymbols
import atlas.icons.materialsymbols.rounded.ContentCopy
import atlas.presentation.core.components.material.Scaffold
import atlas.presentation.core.i18n.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.atlas.presentation.components.AppBar
import com.atlas.presentation.components.AppBarActions
import com.atlas.presentation.util.Screen
import com.atlas.reader.data.backup.models.Backup
import com.atlas.reader.util.system.copyToClipboard
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator

class BackupSchemaScreen : Screen() {

    companion object {
        const val TITLE = "Backup file schema"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val schema = remember { ProtoBufSchemaGenerator.generateSchemaText(Backup.serializer().descriptor) }

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_copy_to_clipboard),
                                    icon = MaterialSymbols.Rounded.ContentCopy,
                                    onClick = {
                                        context.copyToClipboard(TITLE, schema)
                                    },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            Text(
                text = schema,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(16.dp),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
