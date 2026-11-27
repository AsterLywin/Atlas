package com.atlas.desktop

import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuIds
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.model.UiSource

/**
 * The desktop [AtlasRepository]: library reads/writes go to the
 * desktop SQLDelight store, catalogue work goes to the Kotatsu
 * adapters (the same [com.atlas.core.source.Source] contract the old
 * desktop UI used directly).
 */
class DesktopAtlasRepository(
    private val registry: KotatsuParserRegistry,
    private val loaderContext: AtlasMangaLoaderContext,
    private val store: DesktopLibraryStore,
) : AtlasRepository {

    override suspend fun listLibraryBooks(): List<UiBook> =
        store.listBooks().map { it.toUi() }

    override suspend fun listLibraryChapters(bookId: String): List<UiChapter> =
        store.listChapters(bookId).map { it.toUi() }

    override suspend fun getLibraryBook(bookId: String): UiBook? =
        store.getBook(bookId)?.toUi()

    override suspend fun addToLibrary(book: UiBook, chapters: List<UiChapter>) {
        store.addBook(book.toCore(), chapters.map { it.toCore() })
    }

    override suspend fun removeFromLibrary(bookId: String) {
        store.removeBook(bookId)
    }

    override fun listSources(): List<UiSource> =
        registry.sources.map { UiSource(id = "kotatsu:${it.name}", title = it.title) }

    override suspend fun browseBooks(sourceId: String): List<UiBook> =
        adapterFor(sourceId)?.listBooks().orEmpty().map { it.toUi() }

    override suspend fun searchBooks(sourceId: String, query: String): List<UiBook> =
        adapterFor(sourceId)?.searchBooks(query).orEmpty().map { it.toUi() }

    override suspend fun loadChapters(book: UiBook): List<UiChapter> =
        adapterFor(book.sourceId)?.listChapters(book.id).orEmpty().map { it.toUi() }

    override suspend fun loadPages(chapterId: String): List<String> {
        val parsed = KotatsuIds.parseChapterId(chapterId)
            ?: throw IllegalArgumentException("Not a Kotatsu chapter id: $chapterId")
        val adapter = adapterFor("kotatsu:${parsed.book.sourceName}")
            ?: throw IllegalArgumentException("Unknown source for chapter: $chapterId")
        return adapter.listPages(chapterId)
    }

    private fun adapterFor(sourceId: String) =
        registry.newAdapter(sourceId.removePrefix("kotatsu:"), loaderContext)
}

private fun Book.toUi(): UiBook = UiBook(
    id = id,
    title = title,
    author = author,
    sourceId = sourceId,
    coverUrl = coverUrl,
)

private fun Chapter.toUi(): UiChapter = UiChapter(
    id = id,
    bookId = bookId,
    title = title,
    number = number,
    read = read,
    progress = progress,
)

private fun UiBook.toCore(): Book = Book(
    id = id,
    title = title,
    author = author,
    sourceId = sourceId,
    coverUrl = coverUrl,
)

private fun UiChapter.toCore(): Chapter = Chapter(
    id = id,
    bookId = bookId,
    title = title,
    number = number,
    read = read,
    progress = progress,
)
