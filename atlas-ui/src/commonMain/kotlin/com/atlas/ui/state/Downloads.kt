package com.atlas.ui.state

import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import kotlinx.coroutines.flow.StateFlow

/**
 * Download state of one chapter, as the shared UI sees it. It mirrors
 * the core [com.atlas.core.download.DownloadState] on purpose: atlas-core
 * is a JVM-only module and cannot be a commonMain dependency, so — like
 * [com.atlas.ui.model.UiBook] / [com.atlas.ui.model.UiChapter] — the UI
 * carries its own copy of the shape. The desktop implementation maps the
 * core state onto this one.
 */
sealed interface UiDownloadState {
    /** Never downloaded. */
    data object NotDownloaded : UiDownloadState

    /** Waiting for a free download slot. */
    data object Queued : UiDownloadState

    /** Downloading; [progress] is 0..1. */
    data class Downloading(val progress: Float) : UiDownloadState

    /** Fully downloaded and stored locally. */
    data object Downloaded : UiDownloadState

    /** The last attempt failed; [reason] is a short human-readable cause. */
    data class Failed(val reason: String) : UiDownloadState
}

/**
 * Where chapter downloads live. The interface is the seam the task
 * asked for, like [ReadingProgressStore]: the desktop implementation
 * downloads pages with the parser's own HTTP client and stores them
 * under the desktop data dir, the Android one is a no-op stub until
 * Android downloads are built — the shared UI never touches either
 * implementation directly.
 */
interface DownloadManager {
    /** The live state of one chapter; starts as [UiDownloadState.NotDownloaded]. */
    fun state(book: UiBook, chapter: UiChapter): StateFlow<UiDownloadState>

    /** Queue [chapter] for download (no-op when already downloaded or queued). */
    fun download(book: UiBook, chapter: UiChapter)

    /** Cancel a queued or running download; partial files are deleted. */
    fun cancel(book: UiBook, chapter: UiChapter)

    /**
     * Local page locations of a fully downloaded chapter, in reading
     * order — `file:` URIs the reader's image loader can open — or
     * null when the chapter is not fully downloaded. The reader
     * prefers these over the source URLs, so a downloaded chapter
     * reads offline. A platform with no downloads returns null.
     */
    fun localPages(book: UiBook, chapter: UiChapter): List<String>? = null
}
