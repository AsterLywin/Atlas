package com.atlas.desktop

import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.ui.state.ReadingProgress
import com.atlas.ui.state.ReadingProgressStore

/**
 * Reading progress on desktop, stored in the desktop SQLDelight
 * library: [ReadingProgress.fraction] is the chapter's 0..1 progress
 * ([DesktopLibraryStore.markChapterRead]), so the last-read chapter
 * and the book's finished flag update with it. Progress only
 * persists for books in the library — reading a chapter straight
 * from Browse (before adding the book) has no row to write to, and
 * loads back as "start at page 0".
 */
class DesktopProgressStore(
    private val store: DesktopLibraryStore,
) : ReadingProgressStore {

    override fun loadProgress(bookId: String, chapterId: String): ReadingProgress? {
        val chapter = store.listChapters(bookId).firstOrNull { it.id == chapterId }
            ?: return null
        // Zero progress means "never started": no saved position, so the
        // reader opens at page 0 — the same as no row at all.
        if (chapter.progress <= 0f) return null
        return ReadingProgress(bookId, chapterId, chapter.progress)
    }

    override fun saveProgress(progress: ReadingProgress) {
        store.markChapterRead(progress.bookId, progress.chapterId, progress.fraction)
    }
}
