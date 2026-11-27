package com.atlas.ui

import android.content.Context
import com.atlas.ui.model.AppLanguage
import com.atlas.ui.model.AppSettings
import com.atlas.ui.model.AppTheme
import com.atlas.ui.model.ReaderMode
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.state.ReadingProgress
import com.atlas.ui.state.ReadingProgressStore
import com.atlas.ui.state.SettingsStore
import com.atlas.ui.state.UiDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings on Android: SharedPreferences. Same keys as the desktop
 * properties file, so a setting means the same thing on both.
 */
class AndroidSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): AppSettings = AppSettings(
        language = when (prefs.getString(KEY_LANGUAGE, null)) {
            "fa" -> AppLanguage.Fa
            "en" -> AppLanguage.En
            else -> AppLanguage.System
        },
        theme = when (prefs.getString(KEY_THEME, null)) {
            "light" -> AppTheme.Light
            "dark" -> AppTheme.Dark
            else -> AppTheme.System
        },
        readerMode = when (prefs.getString(KEY_READER_MODE, null)) {
            "webtoon" -> ReaderMode.Webtoon
            else -> ReaderMode.Pager
        },
    )

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putString(
                KEY_LANGUAGE,
                when (settings.language) {
                    AppLanguage.Fa -> "fa"
                    AppLanguage.En -> "en"
                    AppLanguage.System -> "system"
                },
            )
            .putString(
                KEY_THEME,
                when (settings.theme) {
                    AppTheme.Light -> "light"
                    AppTheme.Dark -> "dark"
                    AppTheme.System -> "system"
                },
            )
            .putString(
                KEY_READER_MODE,
                when (settings.readerMode) {
                    ReaderMode.Webtoon -> "webtoon"
                    ReaderMode.Pager -> "pager"
                },
            )
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "atlas_shared_ui"
        internal const val KEY_LANGUAGE = "language"
        internal const val KEY_THEME = "theme"
        internal const val KEY_READER_MODE = "reader_mode"
    }
}

/**
 * Reading progress on Android, in memory only.
 *
 * STUB — deliberately not wired to the Android library store: the
 * shared UI must not reach into the Android data module. Progress
 * survives while the activity lives and is lost on process death,
 * until the Android shell wires this interface to its own store.
 */
class InMemoryProgressStore : ReadingProgressStore {
    private val progress = ConcurrentHashMap<String, ReadingProgress>()

    override fun loadProgress(bookId: String, chapterId: String): ReadingProgress? =
        progress["$bookId::$chapterId"]

    override fun saveProgress(progress: ReadingProgress) {
        this.progress["${progress.bookId}::${progress.chapterId}"] = progress
    }
}

/**
 * Chapter downloads on Android: a no-op stub.
 *
 * STUB — Android downloads are not this wave's work: the real Android
 * downloader ([com.atlas.reader.data.download.DownloadManager]) is
 * untouched, and the shared activity must keep compiling. Every
 * chapter reports [UiDownloadState.NotDownloaded] and both actions do
 * nothing, so the download buttons stay visible but inert.
 */
class NoOpDownloadManager : DownloadManager {
    private val states = ConcurrentHashMap<String, MutableStateFlow<UiDownloadState>>()

    override fun state(book: UiBook, chapter: UiChapter): StateFlow<UiDownloadState> =
        states.getOrPut(chapter.id) { MutableStateFlow(UiDownloadState.NotDownloaded) }

    override fun download(book: UiBook, chapter: UiChapter) {
        // No-op: Android downloads are not this wave's work.
    }

    override fun cancel(book: UiBook, chapter: UiChapter) {
        // No-op: Android downloads are not this wave's work.
    }
}
