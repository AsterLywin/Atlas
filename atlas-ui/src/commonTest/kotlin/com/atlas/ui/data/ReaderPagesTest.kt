package com.atlas.ui.data

import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.model.UiSource
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.state.UiDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The reader's page resolution: a fully downloaded chapter reads
 * from its local files, anything else falls back to the source URLs
 * — including a corrupt "downloaded" chapter whose local list is
 * empty, and a platform whose download manager has no local files.
 */
class ReaderPagesTest {

    private val book = UiBook(id = "b1", title = "Fake Manga", sourceId = "kotatsu:FAKE")
    private val chapter = UiChapter(id = "c1", bookId = "b1", title = "First", number = 1f)
    private val urls = listOf("https://img.example.com/1.jpg", "https://img.example.com/2.jpg")
    private val local = listOf("file:///data/001.jpg", "file:///data/002.jpg")

    @Test
    fun `a downloaded chapter reads its local files`() = runTest {
        val pages = resolveReaderPages(
            book,
            chapter,
            FakePageRepository(urls),
            FakeDownloadManager(local),
        )
        assertEquals(local, pages)
    }

    @Test
    fun `a chapter that is not downloaded falls back to the source urls`() = runTest {
        val pages = resolveReaderPages(
            book,
            chapter,
            FakePageRepository(urls),
            FakeDownloadManager(null),
        )
        assertEquals(urls, pages)
    }

    @Test
    fun `an empty local list falls back to the source urls`() = runTest {
        val pages = resolveReaderPages(
            book,
            chapter,
            FakePageRepository(urls),
            FakeDownloadManager(emptyList()),
        )
        assertEquals(urls, pages)
    }

    private class FakePageRepository(
        private val pages: List<String>,
    ) : AtlasRepository {
        override suspend fun listLibraryBooks(): List<UiBook> = emptyList()
        override suspend fun listLibraryChapters(bookId: String): List<UiChapter> = emptyList()
        override suspend fun getLibraryBook(bookId: String): UiBook? = null
        override suspend fun addToLibrary(book: UiBook, chapters: List<UiChapter>) = Unit
        override suspend fun removeFromLibrary(bookId: String) = Unit
        override fun listSources(): List<UiSource> = emptyList()
        override suspend fun browseBooks(sourceId: String): List<UiBook> = emptyList()
        override suspend fun searchBooks(sourceId: String, query: String): List<UiBook> = emptyList()
        override suspend fun loadChapters(book: UiBook): List<UiChapter> = emptyList()
        override suspend fun loadPages(chapterId: String): List<String> = pages
    }

    private class FakeDownloadManager(
        private val localPages: List<String>?,
    ) : DownloadManager {
        override fun state(book: UiBook, chapter: UiChapter): StateFlow<UiDownloadState> =
            MutableStateFlow(UiDownloadState.NotDownloaded)

        override fun download(book: UiBook, chapter: UiChapter) = Unit
        override fun cancel(book: UiBook, chapter: UiChapter) = Unit
        override fun localPages(book: UiBook, chapter: UiChapter): List<String>? = localPages
    }
}
