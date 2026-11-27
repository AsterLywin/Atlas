package com.atlas.reader.ui.shared

import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuIds
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.model.UiSource
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import com.atlas.core.library.Book as CoreBook
import com.atlas.core.library.Chapter as CoreChapter

/**
 * The shared UI's [AtlasRepository] for the Android activity: catalogue
 * work goes to the Kotatsu adapters (the same registry the app's
 * [com.atlas.reader.source.kotatsu.KotatsuSourceProvider] builds its
 * Mihon sources from), the library is in-memory only.
 *
 * The library is deliberately NOT the app's real library: wiring the
 * shared UI to the Android data module is a later step, and the
 * shared UI must not reach into it. Books added here live until the
 * activity is gone.
 */
class AtlasSharedRepository(
    private val registry: KotatsuParserRegistry,
    private val loaderContext: MangaLoaderContext,
) : AtlasRepository {

    private val books = LinkedHashMap<String, UiBook>()
    private val chapters = HashMap<String, List<UiChapter>>()

    override suspend fun listLibraryBooks(): List<UiBook> = books.values.toList()

    override suspend fun listLibraryChapters(bookId: String): List<UiChapter> =
        chapters[bookId].orEmpty()

    override suspend fun getLibraryBook(bookId: String): UiBook? = books[bookId]

    override suspend fun addToLibrary(book: UiBook, chapters: List<UiChapter>) {
        books[book.id] = book
        this.chapters[book.id] = chapters.sortedBy { it.number }
    }

    override suspend fun removeFromLibrary(bookId: String) {
        books.remove(bookId)
        chapters.remove(bookId)
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

private fun CoreBook.toUi(): UiBook = UiBook(
    id = id,
    title = title,
    author = author,
    sourceId = sourceId,
    coverUrl = coverUrl,
)

private fun CoreChapter.toUi(): UiChapter = UiChapter(
    id = id,
    bookId = bookId,
    title = title,
    number = number,
    read = read,
    progress = progress,
)
