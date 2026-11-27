package com.atlas.reader.source.kotatsu

import atlas.domain.chapter.model.Chapter
import com.atlas.parsers.KotatsuIds
import com.atlas.reader.data.cache.ChapterCache
import com.atlas.reader.source.model.Page
import com.atlas.reader.ui.reader.loader.HttpPageLoader
import com.atlas.reader.ui.reader.model.ReaderChapter
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Plain JVM test (Phase 2, task 5): a page download through the real
 * [HttpPageLoader] sends the parser's request headers (Referer) —
 * the same [KotatsuHttpSource.imageRequest] path the reader and the
 * downloader both use.
 *
 * Runs on the host JVM (not Robolectric): OkHttp 5.5 cannot execute
 * under Robolectric, and the loader needs no Android classes here.
 */
class KotatsuPageDownloadHeadersTest {

    @Test
    fun `page download through HttpPageLoader sends the parser Referer`() = runBlocking {
        val server = MockWebServer()
        // The image server only answers when the parser Referer is present.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.getHeader("Referer") == "https://fake.example/") {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(Buffer().write(ByteArray(16) { 0x89.toByte() }))
                        .addHeader("Content-Type", "image/png")
                } else {
                    MockResponse().setResponseCode(403)
                }
            }
        }
        server.start()
        try {
            val source = FakeSource()
            val imageUrl = server.url("/img/1.jpg").toString()
            val parser = mockk<MangaParser> {
                every { this@mockk.source } returns source
                every { availableSortOrders } returns setOf(SortOrder.UPDATED)
                every { domain } returns "fake.example"
                every { getRequestHeaders() } returns Headers.Builder()
                    .add("User-Agent", "test-agent")
                    .add("Referer", "https://fake.example/")
                    .build()
                every { intercept(any()) } answers {
                    val chain = firstArg<Interceptor.Chain>()
                    chain.proceed(chain.request())
                }
                coEvery { getPages(any()) } returns
                    listOf(fakePage(101, "/img/1", source))
                coEvery { getPageUrl(any()) } returns imageUrl
            }
            val bridge = KotatsuHttpSource(parser, AndroidMangaLoaderContext())
            val chapter = Chapter.create().copy(
                url = KotatsuIds.chapterIdOf(
                    KotatsuIds.bookIdOf(source, fakeManga(1, "Fake Manga", source)),
                    fakeChapter(11, 1f, source),
                ),
                name = "Chapter 1",
            )
            val chapterCache = mockk<ChapterCache> {
                every { getPageListFromCache(any()) } throws RuntimeException("no cache")
                every { isImageInCache(any()) } returns false
                every { putImageToCache(any(), any()) } answers { }
            }
            val loader = HttpPageLoader(
                chapter = ReaderChapter(chapter),
                source = bridge,
                chapterCache = chapterCache,
                appScope = CoroutineScope(Dispatchers.Default),
                canLoadAhead = { false },
            )
            val pages = loader.getPages()
            pages shouldHaveSize 1
            val page = pages.single()
            page.imageUrl shouldBe imageUrl
            loader.loadPage(page)
            // The load runs on the loader's own IO scope; poll for completion.
            val deadline = System.currentTimeMillis() + 10_000
            while (page.status !is Page.State.Ready &&
                page.status !is Page.State.Error &&
                System.currentTimeMillis() < deadline
            ) {
                delay(50)
            }
            page.status shouldBe Page.State.Ready
            val recorded = server.takeRequest()
            recorded.getHeader("Referer") shouldBe "https://fake.example/"
            loader.recycle()
        } finally {
            server.shutdown()
        }
    }
}
