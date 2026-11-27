package com.atlas.ui.data

import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.model.UiSource

/**
 * Everything the shared UI needs from a platform: library reads and
 * writes, catalogue browsing/searching, and page URLs for the reader.
 * The platform shell implements it — on desktop over the Kotatsu
 * registry and the desktop SQLDelight store, on Android over the
 * same registry with an in-memory library for now. All network work
 * is suspending; library reads are suspending too so a platform may
 * move them off the UI thread.
 */
interface AtlasRepository {
    suspend fun listLibraryBooks(): List<UiBook>
    suspend fun listLibraryChapters(bookId: String): List<UiChapter>
    suspend fun getLibraryBook(bookId: String): UiBook?
    suspend fun addToLibrary(book: UiBook, chapters: List<UiChapter>)
    suspend fun removeFromLibrary(bookId: String)

    fun listSources(): List<UiSource>
    suspend fun browseBooks(sourceId: String): List<UiBook>
    suspend fun searchBooks(sourceId: String, query: String): List<UiBook>

    /** Chapters of a book that is not in the library (from its source). */
    suspend fun loadChapters(book: UiBook): List<UiChapter>

    /** Direct image URLs of one chapter, in reading order. */
    suspend fun loadPages(chapterId: String): List<String>
}
