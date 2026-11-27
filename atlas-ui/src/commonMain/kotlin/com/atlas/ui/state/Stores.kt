package com.atlas.ui.state

import com.atlas.ui.model.AppSettings

/**
 * Reading progress inside one chapter: the 0..1 fraction read.
 * The fraction — not a page index — is what crosses the platform
 * boundary, because the page count is only known once the pages
 * have loaded; the reader converts the fraction back to an index
 * with [ReaderState.restoredIndex].
 */
data class ReadingProgress(
    val bookId: String,
    val chapterId: String,
    /** 0..1 fraction read inside the chapter. */
    val fraction: Float,
)

/**
 * Where reading progress lives. The interface is the seam the task
 * asked for: the desktop implementation writes into the desktop
 * SQLDelight store, the Android one is an in-memory stub until it is
 * wired to the Android store — the shared UI never touches either
 * store directly.
 */
interface ReadingProgressStore {
    fun loadProgress(bookId: String, chapterId: String): ReadingProgress?
    fun saveProgress(progress: ReadingProgress)
}

/** Where [AppSettings] live. Plain (blocking) calls: both platform
 * implementations are cheap local storage (a properties file on
 * desktop, SharedPreferences on Android). */
interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}
