package com.atlas.ui

import com.atlas.ui.model.AppLanguage
import com.atlas.ui.model.AppSettings
import com.atlas.ui.model.AppTheme
import com.atlas.ui.model.ReaderMode
import com.atlas.ui.state.SettingsStore
import java.io.File
import java.util.Properties

/**
 * Settings on desktop: a `.properties` file under the Atlas data dir.
 * Same keys as the Android SharedPreferences store, so a setting
 * means the same thing on both. A missing or corrupt file reads as
 * the defaults; saving creates the parent dirs.
 */
class FileSettingsStore(private val file: File) : SettingsStore {
    override fun load(): AppSettings {
        val props = Properties()
        if (file.isFile) {
            runCatching { file.inputStream().use { props.load(it) } }
        }
        return AppSettings(
            language = when (props.getProperty(KEY_LANGUAGE)) {
                "fa" -> AppLanguage.Fa
                "en" -> AppLanguage.En
                else -> AppLanguage.System
            },
            theme = when (props.getProperty(KEY_THEME)) {
                "light" -> AppTheme.Light
                "dark" -> AppTheme.Dark
                else -> AppTheme.System
            },
            readerMode = when (props.getProperty(KEY_READER_MODE)) {
                "webtoon" -> ReaderMode.Webtoon
                else -> ReaderMode.Pager
            },
        )
    }

    override fun save(settings: AppSettings) {
        val props = Properties().apply {
            setProperty(
                KEY_LANGUAGE,
                when (settings.language) {
                    AppLanguage.Fa -> "fa"
                    AppLanguage.En -> "en"
                    AppLanguage.System -> "system"
                },
            )
            setProperty(
                KEY_THEME,
                when (settings.theme) {
                    AppTheme.Light -> "light"
                    AppTheme.Dark -> "dark"
                    AppTheme.System -> "system"
                },
            )
            setProperty(
                KEY_READER_MODE,
                when (settings.readerMode) {
                    ReaderMode.Webtoon -> "webtoon"
                    ReaderMode.Pager -> "pager"
                },
            )
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "Atlas settings") }
    }

    companion object {
        internal const val KEY_LANGUAGE = "language"
        internal const val KEY_THEME = "theme"
        internal const val KEY_READER_MODE = "reader_mode"
    }
}
