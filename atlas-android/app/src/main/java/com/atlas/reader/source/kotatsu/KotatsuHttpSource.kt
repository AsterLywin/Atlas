package com.atlas.reader.source.kotatsu

import com.atlas.parsers.KotatsuIds
import com.atlas.parsers.KotatsuIds.toChapter
import com.atlas.parsers.KotatsuIds.toManga
import com.atlas.reader.network.GET
import com.atlas.reader.source.model.FilterList
import com.atlas.reader.source.model.MangasPage
import com.atlas.reader.source.model.Page
import com.atlas.reader.source.model.SChapter
import com.atlas.reader.source.model.SManga
import com.atlas.reader.source.model.SMangaUpdate
import com.atlas.reader.source.online.HttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.generateUid
import java.util.concurrent.ConcurrentHashMap

/**
 * A Kotatsu [MangaParser] exposed as a Mihon [HttpSource].
 *
 * The mapping, in both directions:
 * - [getPopularManga]/[getLatestUpdates]/[getSearchManga] -> `parser.getList`
 *   with an offset accumulated from the sizes of the previous pages
 *   (Kotatsu lists are offset-based; Mihon pages are 1-based).
 * - [getMangaUpdate] -> `parser.getDetails` on the manga rebuilt from the
 *   stateless [KotatsuIds] book id; chapters keep their [Float] number
 *   (1.5 stays 1.5), upload date and scanlator.
 * - [getPageList] -> `parser.getPages` + `parser.getPageUrl` per page, so
 *   [Page.imageUrl] is already the direct image URL.
 * - [SManga.url]/[SChapter.url] hold the stateless Kotatsu ids
 *   (`kotatsu:<SOURCE>:<id>:<base64url>`); they pass through the
 *   database untouched and are parsed back here.
 *
 * The bridge extends [HttpSource] (rather than implementing `Source`
 * directly) because the reader, the downloader, the cover fetcher and
 * the WebView all resolve sources with `as? HttpSource` and silently
 * drop anything else.
 *
 * Source ids are stable: [id] is `generateId("kotatsu:<ENUM_NAME>", locale, 1)`
 * where the name is the parser's unique enum key (e.g. `MANGADEX`), never
 * the display title, and `versionId` is locked at 1. An id must never
 * change once a manga is saved — the library and backups key off it.
 *
 * Parser errors (including [UnsupportedOperationException] from the
 * JavaScript/WebView hooks, which the ~35 parsers that need them hit)
 * propagate to the caller as-is; Mihon shows them as source errors.
 * Filters are not mapped yet: [getFilterList] returns empty.
 */
class KotatsuHttpSource(
    private val parser: MangaParser,
    private val loaderContext: MangaLoaderContext,
) : HttpSource() {

    override val name: String = parser.source.title

    /**
     * Sources with an empty locale (e.g. MangaDex) report `"all"`, which is
     * always in the enabled languages, so they are visible by default.
     */
    override val lang: String = parser.source.locale.ifEmpty { "all" }

    override val versionId: Int = 1

    override val id: Long =
        generateId("kotatsu:${parser.source.name}", parser.source.locale, versionId)

    override val baseUrl: String = "https://${parser.domain}"

    override val supportsLatest: Boolean = SortOrder.UPDATED in parser.availableSortOrders

    /**
     * Image (and other) requests run through the parser's own interceptor
     * chain — image descrambling, per-source headers — on top of the host
     * HTTP client, whose cookie jar is shared with the loader context.
     */
    override val client: OkHttpClient by lazy {
        loaderContext.httpClient.newBuilder()
            .addInterceptor(parser)
            .build()
    }

    /**
     * Covers (and every other request built from [headers]) go through
     * here: [MangaCoverFetcher] builds its request from `source.headers`,
     * so the parser's own request headers — Referer and friends from
     * [MangaParser.getRequestHeaders] — must be in the set, or sources
     * that gate covers on Referer answer 403 while their pages load fine.
     * The parser headers already carry a User-Agent by default; the
     * loader context's is only added when the parser has none.
     */
    override fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        val parserHeaders = parser.getRequestHeaders()
        for (i in 0 until parserHeaders.size) {
            add(parserHeaders.name(i), parserHeaders.value(i))
        }
        if (parserHeaders["User-Agent"] == null) {
            add("User-Agent", loaderContext.getDefaultUserAgent())
        }
    }

    /**
     * Items returned per page, keyed by list (`popular`, `latest`,
     * `search:<query>`). Offsets are cumulative sums of these sizes, which
     * always land exactly on a page boundary for both item-offset parsers
     * and paged parsers.
     */
    private val pageSizes = ConcurrentHashMap<String, MutableMap<Int, Int>>()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val order = if (SortOrder.POPULARITY in parser.availableSortOrders) {
            SortOrder.POPULARITY
        } else {
            preferredOrder()
        }
        return fetchList(LIST_POPULAR, page, order, MangaListFilter.EMPTY)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val order = if (SortOrder.UPDATED in parser.availableSortOrders) {
            SortOrder.UPDATED
        } else {
            preferredOrder()
        }
        return fetchList(LIST_LATEST, page, order, MangaListFilter.EMPTY)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val order = if (SortOrder.RELEVANCE in parser.availableSortOrders) {
            SortOrder.RELEVANCE
        } else {
            preferredOrder()
        }
        return fetchList("$LIST_SEARCH:$query", page, order, MangaListFilter(query = query))
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val parsed = KotatsuIds.parseBookId(manga.url) ?: return SMangaUpdate(manga, chapters)
        val details = parser.getDetails(parser.source.toManga(parsed))
        val updatedManga = if (fetchDetails) {
            manga.copy().apply {
                title = details.title
                thumbnail_url = details.largeCoverUrl ?: details.coverUrl
                author = details.authors.joinToString(", ").takeIf { it.isNotEmpty() }
                description = details.description
                genre = details.tags.joinToString(", ") { it.title }.takeIf { it.isNotEmpty() }
                status = details.state.toStatus()
                initialized = true
            }
        } else {
            manga
        }
        val updatedChapters = if (fetchChapters) {
            details.chapters.orEmpty().map { it.toSChapter(manga.url) }
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parsed = KotatsuIds.parseChapterId(chapter.url) ?: return emptyList()
        val mangaChapter = parser.source.toChapter(parsed)
        return parser.getPages(mangaChapter).mapIndexed { index, mangaPage ->
            Page(index = index, url = mangaPage.url, imageUrl = parser.getPageUrl(mangaPage))
        }
    }

    @OptIn(InternalParsersApi::class)
    override suspend fun getImageUrl(page: Page): String {
        page.imageUrl?.let { return it }
        val mangaPage = MangaPage(
            id = parser.generateUid(page.url),
            url = page.url,
            preview = null,
            source = parser.source,
        )
        return parser.getPageUrl(mangaPage)
    }

    protected override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, parser.getRequestHeaders())
    }

    override fun getMangaUrl(manga: SManga): String {
        val parsed = KotatsuIds.parseBookId(manga.url) ?: return baseUrl
        return parsed.url.toPublicUrl()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parsed = KotatsuIds.parseChapterId(chapter.url) ?: return baseUrl
        return parsed.book.url.toPublicUrl()
    }

    private suspend fun fetchList(
        key: String,
        page: Int,
        order: SortOrder,
        filter: MangaListFilter,
    ): MangasPage {
        val offset = offsetFor(key, page)
        val list = parser.getList(offset = offset, order = order, filter = filter)
        recordPage(key, page, list.size)
        return MangasPage(list.map { it.toSManga() }, hasNextPage = list.isNotEmpty())
    }

    private fun offsetFor(key: String, page: Int): Int {
        if (page <= 1) {
            pageSizes[key] = ConcurrentHashMap()
            return 0
        }
        val sizes = pageSizes[key].orEmpty()
        var offset = 0
        for (p in 1 until page) {
            offset += sizes[p] ?: DEFAULT_PAGE_SIZE
        }
        return offset
    }

    private fun recordPage(key: String, page: Int, count: Int) {
        pageSizes.getOrPut(key) { ConcurrentHashMap() }[page] = count
    }

    private fun preferredOrder(): SortOrder {
        val supported = parser.availableSortOrders
        return listOf(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.NEWEST)
            .firstOrNull { it in supported }
            ?: supported.firstOrNull()
            ?: SortOrder.UPDATED
    }

    private fun String.toPublicUrl(): String {
        if (startsWith("http://") || startsWith("https://")) return this
        return "https://${parser.domain}/${trimStart('/')}"
    }

    private fun Manga.toSManga(): SManga = SManga.create().apply {
        url = KotatsuIds.bookIdOf(parser.source, this@toSManga)
        title = this@toSManga.title
        thumbnail_url = largeCoverUrl ?: coverUrl
        author = authors.joinToString(", ").takeIf { it.isNotEmpty() }
        description = this@toSManga.description
        genre = tags.joinToString(", ") { it.title }.takeIf { it.isNotEmpty() }
        status = state.toStatus()
        initialized = false
    }

    private fun MangaChapter.toSChapter(bookId: String): SChapter = SChapter.create().apply {
        url = KotatsuIds.chapterIdOf(bookId, this@toSChapter)
        name = this@toSChapter.title.orEmpty()
        chapter_number = this@toSChapter.number
        scanlator = this@toSChapter.scanlator
        date_upload = this@toSChapter.uploadDate
    }

    private fun MangaState?.toStatus(): Int = when (this) {
        MangaState.ONGOING -> SManga.ONGOING
        MangaState.FINISHED -> SManga.COMPLETED
        MangaState.ABANDONED -> SManga.CANCELLED
        MangaState.PAUSED -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val LIST_POPULAR = "popular"
        private const val LIST_LATEST = "latest"
        private const val LIST_SEARCH = "search"
        private const val DEFAULT_PAGE_SIZE = 20
    }
}
