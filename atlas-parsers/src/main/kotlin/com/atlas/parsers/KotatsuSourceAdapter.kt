package com.atlas.parsers

import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import com.atlas.core.source.Source
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * A Kotatsu [MangaParser] exposed as an Atlas [Source].
 *
 * Mapping:
 * - [listBooks]    -> `parser.getList` (first page, best available order)
 * - [searchBooks]  -> `parser.getList` with a query filter, relevance order
 *   when the parser supports it
 * - [listChapters] -> `parser.getDetails` (chapters of the rebuilt [Manga])
 * - [listPages]    -> `parser.getPages` + `parser.getPageUrl` per page
 *
 * Ids are stateless (see [KotatsuIds]): no cache is kept, so an adapter
 * stays valid across restarts. Network errors surface as-is from the
 * parser (its exceptions are `ParseException` / `AuthRequiredException` /
 * plain `IOException`); the caller decides how to show them.
 *
 * Chapter titles come from the parser as-is; when a parser gives no title
 * the Atlas [Chapter.title] is empty and the UI layer supplies its
 * localized fallback from strings.xml — no user-facing text is hardcoded
 * here.
 */
class KotatsuSourceAdapter(
    private val parser: MangaParser,
    @Suppress("unused") private val context: MangaLoaderContext,
) : Source {

    override val id: String = "kotatsu:${parser.source.name}"
    override val name: String = parser.source.title
    override val language: String = parser.source.locale.ifEmpty { "und" }
    override val supportsListing: Boolean = true

    override suspend fun listBooks(): List<Book> =
        parser.getList(offset = 0, order = preferredOrder(), filter = MangaListFilter.EMPTY)
            .map { it.toBook() }

    override suspend fun searchBooks(query: String): List<Book> =
        parser.getList(
            offset = 0,
            order = searchOrder(),
            filter = MangaListFilter(query = query),
        ).map { it.toBook() }

    override suspend fun listChapters(bookId: String): List<Chapter> {
        val parsed = KotatsuIds.parseBookId(bookId)
            ?: throw IllegalArgumentException("Not a Kotatsu book id: $bookId")
        val source = parser.source
        val manga = with(KotatsuIds) { source.toManga(parsed) }
        val details = parser.getDetails(manga)
        val chapters = details.chapters.orEmpty()
        return chapters.mapIndexed { index, chapter ->
            chapter.toChapter(bookId, index)
        }
    }

    override suspend fun listPages(chapterId: String): List<String> {
        val parsed = KotatsuIds.parseChapterId(chapterId)
            ?: throw IllegalArgumentException("Not a Kotatsu chapter id: $chapterId")
        val source = parser.source
        val chapter = with(KotatsuIds) { source.toChapter(parsed) }
        return parser.getPages(chapter).map { page -> parser.getPageUrl(page) }
    }

    /**
     * Best list order this parser supports: freshest first, then most
     * popular, then whatever the parser offers first.
     */
    private fun preferredOrder(): SortOrder {
        val supported = parser.availableSortOrders
        return listOf(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST)
            .firstOrNull { it in supported }
            ?: supported.firstOrNull()
            ?: SortOrder.UPDATED
    }

    private fun searchOrder(): SortOrder {
        val supported = parser.availableSortOrders
        return if (SortOrder.RELEVANCE in supported) SortOrder.RELEVANCE else preferredOrder()
    }

    private fun Manga.toBook(): Book = Book(
        id = KotatsuIds.bookIdOf(parser.source, this),
        title = title,
        author = authors.firstOrNull(),
        sourceId = this@KotatsuSourceAdapter.id,
        coverUrl = largeCoverUrl ?: coverUrl,
    )

    private fun MangaChapter.toChapter(bookId: String, index: Int): Chapter {
        // Parser numbers are floats (1, 1.5, 2...); Atlas numbers are
        // floats too, so 1.5 stays 1.5. Unknown numbers (0) fall back to
        // list position.
        val n = number.takeIf { it > 0f } ?: (index + 1).toFloat()
        return Chapter(
            id = KotatsuIds.chapterIdOf(bookId, this),
            bookId = bookId,
            title = title.orEmpty(),
            number = n,
        )
    }
}
