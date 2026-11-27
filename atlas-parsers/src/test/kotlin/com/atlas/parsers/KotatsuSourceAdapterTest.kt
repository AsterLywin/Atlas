package com.atlas.parsers

import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.Favicons
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities

/**
 * A parser that answers from fixed data and never touches the network.
 * Exercises the mapping in [KotatsuSourceAdapter], not the parsers.
 */
@Suppress("DEPRECATION")
private class FakeParser : MangaParser {
    override val source = MangaParserSource.MANGADEX
    override val availableSortOrders = setOf(SortOrder.UPDATED, SortOrder.RELEVANCE)
    override val searchQueryCapabilities = MangaSearchQueryCapabilities()
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override val config: MangaSourceConfig = object : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }
    override val authorizationProvider: MangaParserAuthProvider? = null
    override val configKeyDomain = ConfigKey.Domain("example.com")
    override val domain: String = "example.com"

    val manga = Manga(
        id = 1L,
        title = "Fake Manga",
        altTitles = emptySet(),
        url = "/manga/1",
        publicUrl = "https://example.com/manga/1",
        rating = 0.8f,
        contentRating = null,
        coverUrl = "https://example.com/cover.jpg",
        tags = emptySet(),
        state = null,
        authors = setOf("Fake Author"),
        source = source,
    )
    val chapters = listOf(
        MangaChapter(10L, "First", 1f, 0, "/manga/1/ch-1", null, 0L, null, source),
        MangaChapter(11L, null, 2f, 0, "/manga/1/ch-2", null, 0L, null, source),
        MangaChapter(12L, "Half", 2.5f, 0, "/manga/1/ch-3", null, 0L, null, source),
    )
    val pages = listOf(
        MangaPage(100L, "/page/100", null, source),
        MangaPage(101L, "/page/101", null, source),
    )

    @Deprecated("Too complex. Use getList with filter instead")
    override suspend fun getList(query: MangaSearchQuery): List<Manga> = listOf(manga)

    override suspend fun getList(
        offset: Int,
        order: SortOrder,
        filter: MangaListFilter,
    ): List<Manga> = if (filter.query == "fake") listOf(manga) else listOf(manga)

    override suspend fun getDetails(manga: Manga): Manga = manga.copy(chapters = chapters)
    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = pages
    override suspend fun getPageUrl(page: MangaPage): String =
        "https://img.example.com/${page.id}.jpg"

    override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()
    override suspend fun getFavicons(): Favicons = Favicons(emptyList(), null)
    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) = Unit
    override suspend fun getRelatedManga(seed: Manga): List<Manga> = emptyList()
    override fun getRequestHeaders(): Headers = Headers.headersOf()
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}

class KotatsuSourceAdapterTest {

    private val context = AtlasMangaLoaderContext()
    private val adapter = KotatsuSourceAdapter(FakeParser(), context)

    @Test
    fun `identity comes from the parser source`() {
        assertEquals("kotatsu:MANGADEX", adapter.id)
        assertEquals("MangaDex", adapter.name)
        assertEquals("und", adapter.language)
        assertTrue(adapter.supportsListing)
    }

    @Test
    fun `listBooks maps manga to books`() = runTest {
        val books = adapter.listBooks()
        assertEquals(1, books.size)
        assertEquals("Fake Manga", books[0].title)
        assertEquals("Fake Author", books[0].author)
        assertEquals("https://example.com/cover.jpg", books[0].coverUrl)
        assertEquals("kotatsu:MANGADEX", books[0].sourceId)
        assertTrue(books[0].id.startsWith("kotatsu:MANGADEX:1:"))
    }

    @Test
    fun `listChapters maps parser chapters in order`() = runTest {
        val bookId = adapter.listBooks()[0].id
        val chapters = adapter.listChapters(bookId)
        assertEquals(3, chapters.size)
        assertEquals("First", chapters[0].title)
        assertEquals(1f, chapters[0].number)
        // No title from the parser: empty, the UI supplies the fallback.
        assertEquals("", chapters[1].title)
        assertEquals(2f, chapters[1].number)
        // Fractional number 2.5 stays 2.5 now that Atlas numbers are floats.
        assertEquals(2.5f, chapters[2].number)
        assertEquals(bookId, chapters[0].bookId)
    }

    @Test
    fun `listPages resolves every page to an image url`() = runTest {
        val bookId = adapter.listBooks()[0].id
        val chapterId = adapter.listChapters(bookId)[0].id
        assertEquals(
            listOf(
                "https://img.example.com/100.jpg",
                "https://img.example.com/101.jpg",
            ),
            adapter.listPages(chapterId),
        )
    }

    @Test
    fun `foreign ids are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { adapter.listChapters("local:abc") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { adapter.listPages("local:abc") }
        }
    }
}
