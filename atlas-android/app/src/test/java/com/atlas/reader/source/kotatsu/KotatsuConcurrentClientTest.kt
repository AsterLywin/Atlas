package com.atlas.reader.source.kotatsu

import com.atlas.parsers.InMemoryCookieJar
import com.atlas.parsers.KotatsuIds
import com.atlas.reader.source.model.SManga
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.Manga as KotatsuManga

/**
 * Plain JVM test (Phase 2, task 4b): two concurrent manga updates
 * through one bridge share a single HTTP client and cookie jar.
 *
 * Under Robolectric no real OkHttp call can execute (OkHttp 5.5
 * references an API 37 class missing from android-all), so this runs
 * on the host JVM where OkHttp falls back to the JDK platform.
 * The parser here is a mockk whose `getDetails` performs a real GET
 * through the loader context's shared client.
 */
class KotatsuConcurrentClientTest {

    @Test
    fun `two concurrent manga updates share one client and cookie jar`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val jar = InMemoryCookieJar()
            val client = OkHttpClient.Builder().cookieJar(jar).build()
            val loaderContext = AndroidMangaLoaderContext(httpClient = client, cookieJar = jar)
            val source = FakeSource()
            val parser = mockk<MangaParser> {
                every { this@mockk.source } returns source
                every { availableSortOrders } returns setOf(SortOrder.UPDATED)
                every { domain } returns "fake.example"
                every { getRequestHeaders() } returns Headers.Builder()
                    .add("User-Agent", "test-agent")
                    .build()
                every { intercept(any()) } answers {
                    val chain = firstArg<Interceptor.Chain>()
                    chain.proceed(chain.request())
                }
                coEvery { getDetails(any()) } answers {
                    val manga = firstArg<KotatsuManga>()
                    val request = Request.Builder()
                        .url(server.url("/details/${manga.id}"))
                        .build()
                    loaderContext.httpClient.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "details HTTP ${response.code}" }
                    }
                    fakeManga(manga.id, "Fake Manga", source).copy(
                        chapters = listOf(fakeChapter(11, 1f, source)),
                    )
                }
            }
            val bridge = KotatsuHttpSource(parser, loaderContext)
            val manga1 = SManga.create().apply {
                url = KotatsuIds.bookIdOf(source, fakeManga(1, "Fake Manga", source))
                title = "Fake Manga"
            }
            val manga2 = SManga.create().apply {
                url = KotatsuIds.bookIdOf(source, fakeManga(2, "Fake Manga", source))
                title = "Fake Manga"
            }

            // First call, sequential: the server sets the session cookie.
            server.enqueue(MockResponse().setBody("{}").addHeader("Set-Cookie", "session=abc; Path=/"))
            val warmup = bridge.getMangaUpdate(manga1, emptyList(), fetchDetails = true, fetchChapters = true)
            warmup.chapters shouldHaveSize 1
            val warmupRequest = server.takeRequest()
            warmupRequest.getHeader("Cookie") shouldBe null

            // Then two updates at once: both must succeed, and both
            // requests must carry the cookie from the shared jar.
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("{}"))
            val first = async(Dispatchers.IO) {
                bridge.getMangaUpdate(manga1, emptyList(), fetchDetails = true, fetchChapters = true)
            }
            val second = async(Dispatchers.IO) {
                bridge.getMangaUpdate(manga2, emptyList(), fetchDetails = true, fetchChapters = true)
            }
            first.await().chapters shouldHaveSize 1
            second.await().chapters shouldHaveSize 1

            val concurrentRequests = listOf(server.takeRequest(), server.takeRequest())
            concurrentRequests.forEach { it.getHeader("Cookie") shouldBe "session=abc" }
        } finally {
            server.shutdown()
        }
    }
}
