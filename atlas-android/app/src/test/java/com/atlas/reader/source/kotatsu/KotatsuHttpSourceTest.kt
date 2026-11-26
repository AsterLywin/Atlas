package com.atlas.reader.source.kotatsu

import com.atlas.parsers.KotatsuIds
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.reader.source.model.FilterList
import com.atlas.reader.source.model.Page
import com.atlas.reader.source.model.SChapter
import com.atlas.reader.source.model.SManga
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.Base64

class KotatsuHttpSourceTest {

    private fun fakeParser(
        source: FakeSource,
        orders: Set<SortOrder> = setOf(
            SortOrder.UPDATED,
            SortOrder.POPULARITY,
            SortOrder.RELEVANCE,
        ),
    ): MangaParser = mockk {
        every { this@mockk.source } returns source
        every { availableSortOrders } returns orders
        every { domain } returns "fake.example"
        every { getRequestHeaders() } returns Headers.Builder()
            .add("User-Agent", "test-agent")
            .build()
    }

    private fun fakeContext(): MangaLoaderContext = mockk {
        every { httpClient } returns OkHttpClient()
        every { getDefaultUserAgent() } returns "test-agent"
    }

    @Test
    fun `id is stable across instances and never zero`() {
        val parser = fakeParser(FakeSource())
        val first = KotatsuHttpSource(parser, fakeContext()).id
        val second = KotatsuHttpSource(parser, fakeContext()).id
        first shouldBe second
        (first > 0L) shouldBe true
    }

    @Test
    fun `empty locale maps to all`() {
        val noLocale = KotatsuHttpSource(fakeParser(FakeSource(locale = "")), fakeContext())
        noLocale.lang shouldBe "all"
        val english = KotatsuHttpSource(fakeParser(FakeSource(locale = "en")), fakeContext())
        english.lang shouldBe "en"
    }

    @Test
    fun `supportsLatest follows UPDATED in availableSortOrders`() {
        val without = KotatsuHttpSource(
            fakeParser(FakeSource(), setOf(SortOrder.POPULARITY)),
            fakeContext(),
        )
        without.supportsLatest shouldBe false
        val with = KotatsuHttpSource(
            fakeParser(FakeSource(), setOf(SortOrder.UPDATED)),
            fakeContext(),
        )
        with.supportsLatest shouldBe true
    }

    @Test
    fun `popular list paginates with cumulative offsets`() = runTest {
        val source = FakeSource()
        val parser = fakeParser(source)
        val page1 = (1..20).map { fakeManga(it.toLong(), "Manga $it", source) }
        val page2 = (21..40).map { fakeManga(it.toLong(), "Manga $it", source) }
        coEvery { parser.getList(any(), any(), any()) } answers {
            when (firstArg<Int>()) {
                0 -> page1
                20 -> page2
                else -> emptyList()
            }
        }
        val bridge = KotatsuHttpSource(parser, fakeContext())

        val first = bridge.getPopularManga(1)
        first.mangas shouldHaveSize 20
        first.hasNextPage shouldBe true
        first.mangas.first().title shouldBe "Manga 1"
        first.mangas.first().url.startsWith("kotatsu:FAKESOURCE:") shouldBe true
        first.mangas.first().initialized shouldBe false

        val second = bridge.getPopularManga(2)
        second.mangas shouldHaveSize 20
        second.mangas.first().title shouldBe "Manga 21"

        val third = bridge.getPopularManga(3)
        third.mangas shouldHaveSize 0
        third.hasNextPage shouldBe false

        coVerify(exactly = 1) { parser.getList(0, SortOrder.POPULARITY, any()) }
        coVerify(exactly = 1) { parser.getList(20, SortOrder.POPULARITY, any()) }
        coVerify(exactly = 1) { parser.getList(40, SortOrder.POPULARITY, any()) }
    }

    @Test
    fun `search passes the query and prefers RELEVANCE`() = runTest {
        val source = FakeSource()
        val parser = fakeParser(source)
        coEvery { parser.getList(any(), any(), any()) } returns
            listOf(fakeManga(1, "One Piece", source))
        val bridge = KotatsuHttpSource(parser, fakeContext())

        val result = bridge.getSearchManga(1, "one piece", FilterList())
        result.mangas shouldHaveSize 1
        coVerify { parser.getList(0, SortOrder.RELEVANCE, match { it.query == "one piece" }) }
    }

    @Test
    fun `manga update maps details and keeps float chapter numbers`() = runTest {
        val source = FakeSource()
        val parser = fakeParser(source)
        val manga = fakeManga(1, "Manga 1", source)
        val details = manga.copy(
            description = "Full description",
            chapters = listOf(
                fakeChapter(11, 1f, source),
                fakeChapter(12, 1.5f, source),
            ),
        )
        coEvery { parser.getDetails(any()) } returns details
        val bridge = KotatsuHttpSource(parser, fakeContext())
        val smanga = SManga.create().apply {
            url = KotatsuIds.bookIdOf(source, manga)
            title = "Manga 1"
        }

        val update = bridge.getMangaUpdate(smanga, emptyList(), fetchDetails = true, fetchChapters = true)
        update.manga.description shouldBe "Full description"
        update.manga.initialized shouldBe true
        update.manga.status shouldBe SManga.ONGOING
        update.manga.author shouldBe "Author 1"
        update.manga.genre shouldBe "Action"
        update.chapters shouldHaveSize 2
        update.chapters[1].chapter_number shouldBe 1.5f
        update.chapters[1].scanlator shouldBe "Scan Team"
        update.chapters[1].date_upload shouldBe 1_700_000_000_000L
        update.chapters[1].url.startsWith("kotatsu:FAKESOURCE:") shouldBe true
    }

    @Test
    fun `manga update without flags does not touch the network`() = runTest {
        val parser = fakeParser(FakeSource())
        val bridge = KotatsuHttpSource(parser, fakeContext())
        val smanga = SManga.create().apply {
            url = "kotatsu:FAKESOURCE:1:aGk"
            title = "X"
        }
        val chapters = emptyList<SChapter>()

        val update = bridge.getMangaUpdate(smanga, chapters, fetchDetails = false, fetchChapters = false)
        update.manga shouldBe smanga
        update.chapters shouldBe chapters
        coVerify(exactly = 0) { parser.getDetails(any()) }
    }

    @Test
    fun `page list fills imageUrl from getPageUrl`() = runTest {
        val source = FakeSource()
        val parser = fakeParser(source)
        val chapter = fakeChapter(11, 1f, source)
        coEvery { parser.getPages(any()) } returns listOf(
            fakePage(101, "/manga/1/chapter/11/1", source),
            fakePage(102, "/manga/1/chapter/11/2", source),
        )
        coEvery { parser.getPageUrl(any()) } answers {
            "https://img.fake.example" + firstArg<MangaPage>().url + ".jpg"
        }
        val bridge = KotatsuHttpSource(parser, fakeContext())
        val schapter = SChapter.create().apply {
            url = KotatsuIds.chapterIdOf(
                KotatsuIds.bookIdOf(source, fakeManga(1, "Manga 1", source)),
                chapter,
            )
        }

        val pages = bridge.getPageList(schapter)
        pages shouldHaveSize 2
        pages[0].imageUrl shouldBe "https://img.fake.example/manga/1/chapter/11/1.jpg"
        pages[0].url shouldBe "/manga/1/chapter/11/1"
        pages[1].index shouldBe 1
    }

    @Test
    fun `imageUrl falls back to the parser when the page has none`() = runTest {
        val source = FakeSource()
        val parser = fakeParser(source)
        coEvery { parser.getPageUrl(any()) } returns "https://img.fake.example/direct.jpg"
        val bridge = KotatsuHttpSource(parser, fakeContext())

        val page = Page(index = 0, url = "/manga/1/chapter/11/1")
        bridge.getImageUrl(page) shouldBe "https://img.fake.example/direct.jpg"

        val withUrl = Page(
            index = 0,
            url = "/manga/1/chapter/11/1",
            imageUrl = "https://img.fake.example/cached.jpg",
        )
        bridge.getImageUrl(withUrl) shouldBe "https://img.fake.example/cached.jpg"
    }

    @Test
    fun `parser errors propagate`() = runTest {
        val parser = fakeParser(FakeSource())
        coEvery { parser.getList(any(), any(), any()) } throws
            RuntimeException("boom")
        val bridge = KotatsuHttpSource(parser, fakeContext())

        assertThrows<RuntimeException> { bridge.getPopularManga(1) }
    }

    @Test
    fun `image download runs through the parser interceptor`() = runTest {
        // Plain JVM test (no Robolectric): on the host JVM OkHttp falls
        // back to the JDK platform, so a real request really runs. In
        // the Robolectric sandbox the same call crashes in
        // Android10Platform (API 37 class missing from android-all).
        val server = MockWebServer()
        server.start()
        try {
            // 1x1 red PNG.
            val png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            )
            server.enqueue(
                MockResponse()
                    .setBody(Buffer().write(png))
                    .addHeader("Content-Type", "image/png"),
            )
            val imageUrl = server.url("/img.png").toString()
            val source = FakeSource()
            val parser = fakeParser(source)
            every { parser.intercept(any()) } answers {
                val chain = firstArg<Interceptor.Chain>()
                val request = chain.request().newBuilder()
                    .addHeader("X-Intercepted", "yes")
                    .build()
                chain.proceed(request)
            }
            coEvery { parser.getPageUrl(any()) } returns imageUrl
            val bridge = KotatsuHttpSource(parser, AndroidMangaLoaderContext())

            val page = Page(index = 0, url = "/img/1", imageUrl = imageUrl)
            val response = bridge.getImage(page)
            response.isSuccessful shouldBe true
            response.body.bytes().contentEquals(png) shouldBe true
            val recorded = server.takeRequest()
            recorded.path shouldBe "/img.png"
            recorded.getHeader("X-Intercepted") shouldBe "yes"
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `configOverrides change a real parser domain`() {
        val context = AndroidMangaLoaderContext(
            configOverrides = mapOf("MANGAKAKALOT" to mapOf("domain" to "127.0.0.1:9999")),
        )
        val registry = KotatsuParserRegistry()
        val source = registry.findSource("MANGAKAKALOT")
            ?: error("MANGAKAKALOT not in registry")
        val parser = registry.newParser(source, context)
        parser.domain shouldBe "127.0.0.1:9999"
    }
}
