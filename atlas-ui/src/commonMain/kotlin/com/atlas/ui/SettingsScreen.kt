package com.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.ui.model.AppLanguage
import com.atlas.ui.model.AppSettings
import com.atlas.ui.model.AppTheme
import com.atlas.ui.model.ReaderMode
import com.atlas.ui.state.BackupOutcome
import com.atlas.ui.theme.LocalAtlasStrings

/**
 * Language, theme and the default reader mode. The backup section
 * only appears when the platform supplies the actions (desktop);
 * without them the section is hidden, not disabled.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    backupStatus: BackupOutcome? = null,
    onCreateBackup: (() -> Unit)? = null,
    onRestoreBackup: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.tabSettings, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text(strings.settingsLanguage, style = MaterialTheme.typography.titleMedium)
        SettingOption(
            text = strings.settingsLanguageSystem,
            selected = settings.language == AppLanguage.System,
            onClick = { onSettingsChange(settings.copy(language = AppLanguage.System)) },
        )
        SettingOption(
            text = strings.settingsLanguageFa,
            selected = settings.language == AppLanguage.Fa,
            onClick = { onSettingsChange(settings.copy(language = AppLanguage.Fa)) },
        )
        SettingOption(
            text = strings.settingsLanguageEn,
            selected = settings.language == AppLanguage.En,
            onClick = { onSettingsChange(settings.copy(language = AppLanguage.En)) },
        )

        Spacer(Modifier.height(16.dp))
        Text(strings.settingsTheme, style = MaterialTheme.typography.titleMedium)
        SettingOption(
            text = strings.settingsThemeSystem,
            selected = settings.theme == AppTheme.System,
            onClick = { onSettingsChange(settings.copy(theme = AppTheme.System)) },
        )
        SettingOption(
            text = strings.settingsThemeLight,
            selected = settings.theme == AppTheme.Light,
            onClick = { onSettingsChange(settings.copy(theme = AppTheme.Light)) },
        )
        SettingOption(
            text = strings.settingsThemeDark,
            selected = settings.theme == AppTheme.Dark,
            onClick = { onSettingsChange(settings.copy(theme = AppTheme.Dark)) },
        )

        Spacer(Modifier.height(16.dp))
        Text(strings.settingsReaderMode, style = MaterialTheme.typography.titleMedium)
        SettingOption(
            text = strings.readerModePager,
            selected = settings.readerMode == ReaderMode.Pager,
            onClick = { onSettingsChange(settings.copy(readerMode = ReaderMode.Pager)) },
        )
        SettingOption(
            text = strings.readerModeWebtoon,
            selected = settings.readerMode == ReaderMode.Webtoon,
            onClick = { onSettingsChange(settings.copy(readerMode = ReaderMode.Webtoon)) },
        )

        if (onCreateBackup != null && onRestoreBackup != null) {
            Spacer(Modifier.height(16.dp))
            Text(strings.settingsBackup, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onCreateBackup) { Text(strings.backupCreate) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onRestoreBackup) { Text(strings.backupRestore) }
            }
            backupStatus?.let { status ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (status) {
                        is BackupOutcome.Created ->
                            strings.backupCreated(status.books, status.fileName)
                        is BackupOutcome.Restored ->
                            if (status.skipped > 0) {
                                strings.backupRestoredSkipped(
                                    status.books,
                                    status.chapters,
                                    status.skipped,
                                )
                            } else {
                                strings.backupRestored(status.books, status.chapters)
                            }
                        BackupOutcome.Cancelled -> strings.backupCancelled
                        is BackupOutcome.Failed -> strings.backupFailed(status.reason)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
