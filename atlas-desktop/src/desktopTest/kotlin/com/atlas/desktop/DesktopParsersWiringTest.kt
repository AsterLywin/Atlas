package com.atlas.desktop

import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.parsers.KotatsuSourceAdapter
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaLoaderContext
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
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities

/**
 * The desktop is wired to the real parser stack: the registry lists
 * the Kotatsu sources, and a [KotatsuSourceAdapter] serves list/search
 * over the core `Source` contract. Pure JVM tests, no Robolectric:
 * the "network" is a local [MockWebServer], the parser answers from
 * it with a real HTTP call through the loader context's client.
 */
class DesktopParsersWiringTest {

    @Test
    fun `registry lists live sources and adapts one`() {
        val registry = KotatsuParserRegistry()
        assertTrue(registry.count > 0, "expected live sources, got ${registry.count}")
        assertNotNull(registry.findSource("MANGADEX"))
        val context = AtlasMangaLoaderContext()
        val adapter = registry.newAdapter("MANGADEX", context)
        assertNotNull(adapter)
        assertEquals("kotatsu:MANGADEX", adapter?.id)
        assertEquals("MangaDex", adapter?.name)
        assertNull(registry.newAdapter("NO_SUCH_SOURCE", context))
    }

    @Test
    fun `listBooks and searchBooks run against a local server`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody("1|Fake Manga|Fake Author\n2|Other Manga|Other\n"),
            )
            server.enqueue(MockResponse().setBody("1|Fake Manga|Fake Author\n"))
            val context = AtlasMangaLoaderContext()
            val adapter = KotatsuSourceAdapter(FakeHttpParser(server, context), context)

            val books = runBlocking { adapter.listBooks() }
            assertEquals(listOf("Fake Manga", "Other Manga"), books.map { it.title })
            assertEquals("Fake Author", books[0].author)
            assertEquals("kotatsu:MANGADEX", books[0].sourceId)
            assertEquals("/list", server.takeRequest().path)

            val found = runBlocking { adapter.searchBooks("fake") }
            assertEquals(listOf("Fake Manga"), found.map { it.title })
            assertEquals("/search?q=fake", server.takeRequest().path)

            // Fractional chapter numbers survive the adapter mapping.
            val chapters = runBlocking { adapter.listChapters(books[0].id) }
            assertEquals(listOf(1f, 2f, 2.5f), chapters.map { it.number })
            assertEquals("Half", chapters[2].title)
        } finally {
            server.shutdown()
        }
    }
}

/**
 * A parser that answers from fixed data over HTTP: its list endpoint
 * is the [MockWebServer], fetched with the loader context's real
 * client. One book per line: `id|title|author`.
 */
@Suppress("DEPRECATION")
private class FakeHttpParser(
    private val server: MockWebServer,
    private val context: MangaLoaderContext,
) : MangaParser {
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

    private val chapters = listOf(
        MangaChapter(10L, "First", 1f, 0, "/manga/1/ch-1", null, 0L, null, source),
        MangaChapter(11L, null, 2f, 0, "/manga/1/ch-2", null, 0L, null, source),
        MangaChapter(12L, "Half", 2.5f, 0, "/manga/1/ch-3", null, 0L, null, source),
    )

    private fun parseBooks(body: String): List<Manga> =
        body.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("|")
                Manga(
                    id = parts[0].toLong(),
                    title = parts[1],
                    altTitles = emptySet(),
                    url = "/manga/${parts[0]}",
                    publicUrl = "https://example.com/manga/${parts[0]}",
                    rating = 0.8f,
                    contentRating = null,
                    coverUrl = null,
                    tags = emptySet(),
                    state = null,
                    authors = setOf(parts.getOrNull(2).orEmpty()),
                    source = source,
                )
            }
            .toList()

    override suspend fun getList(query: MangaSearchQuery): List<Manga> =
        getList(offset = 0, order = SortOrder.UPDATED, filter = MangaListFilter.EMPTY)

    override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = if (filter.query.isNullOrBlank()) {
            server.url("/list")
        } else {
            server.url("/search").newBuilder().addQueryParameter("q", filter.query).build()
        }
        val request = Request.Builder().url(url).build()
        context.httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "list request failed: ${response.code}" }
            return parseBooks(response.body.string())
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = manga.copy(chapters = chapters)

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> =
        listOf(MangaPage(100L, "/page/100", null, source))

    override suspend fun getPageUrl(page: MangaPage): String =
        "https://img.example.com/${page.id}.jpg"

    override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()
    override suspend fun getFavicons(): Favicons = Favicons(emptyList(), null)
    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) = Unit
    override suspend fun getRelatedManga(seed: Manga): List<Manga> = emptyList()
    override fun getRequestHeaders(): Headers = Headers.headersOf()
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
