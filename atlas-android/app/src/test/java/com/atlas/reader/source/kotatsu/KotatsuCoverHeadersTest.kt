package com.atlas.reader.source.kotatsu

import com.atlas.reader.network.GET
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Plain JVM tests for the cover headers (Phase 2, task 1).
 *
 * [MangaCoverFetcher] builds its request from `source.headers`, so the
 * parser's own request headers — Referer and friends from
 * [MangaParser.getRequestHeaders] — must be in the set. Before the fix
 * only the User-Agent was there, so sources that gate covers on
 * Referer answered 403 while their pages loaded fine.
 */
class KotatsuCoverHeadersTest {

    private fun fakeParser(source: FakeSource, headers: Headers): MangaParser = mockk {
        every { this@mockk.source } returns source
        every { availableSortOrders } returns setOf(SortOrder.UPDATED)
        every { domain } returns "fake.example"
        every { getRequestHeaders() } returns headers
        every { intercept(any()) } answers {
            val chain = firstArg<Interceptor.Chain>()
            chain.proceed(chain.request())
        }
    }

    private fun fakeContext(): MangaLoaderContext = mockk {
        every { httpClient } returns OkHttpClient()
        every { getDefaultUserAgent() } returns "test-agent"
    }

    @Test
    fun `headers include the parser Referer`() {
        val parserHeaders = Headers.Builder()
            .add("User-Agent", "parser-agent")
            .add("Referer", "https://fake.example/")
            .build()
        val bridge = KotatsuHttpSource(fakeParser(FakeSource(), parserHeaders), fakeContext())

        bridge.headers["Referer"] shouldBe "https://fake.example/"
        // The parser's own User-Agent wins; there is exactly one of it.
        bridge.headers["User-Agent"] shouldBe "parser-agent"
        bridge.headers.values("User-Agent") shouldHaveSize 1
    }

    @Test
    fun `headers fall back to the loader user agent`() {
        val parserHeaders = Headers.Builder()
            .add("Referer", "https://fake.example/")
            .build()
        val bridge = KotatsuHttpSource(fakeParser(FakeSource(), parserHeaders), fakeContext())

        bridge.headers["Referer"] shouldBe "https://fake.example/"
        bridge.headers["User-Agent"] shouldBe "test-agent"
    }

    @Test
    fun `cover request with bridge headers passes a Referer gate`() {
        // The request is built exactly the way MangaCoverFetcher builds
        // it (MangaCoverFetcher.kt:183-187): url + source.headers.
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.getHeader("Referer") == "https://fake.example/") {
                    MockResponse().setResponseCode(200).setBody("cover-bytes")
                } else {
                    MockResponse().setResponseCode(403)
                }
            }
        }
        server.start()
        try {
            val parserHeaders = Headers.Builder()
                .add("User-Agent", "parser-agent")
                .add("Referer", "https://fake.example/")
                .build()
            val bridge = KotatsuHttpSource(fakeParser(FakeSource(), parserHeaders), fakeContext())
            val url = server.url("/cover/1.jpg").toString()

            // Without the Referer the gate answers 403.
            val bare = OkHttpClient().newCall(GET(url, Headers.Builder().build())).execute()
            bare.code shouldBe 403
            bare.close()

            // With the bridge headers — the exact headers the cover
            // fetcher sends — the same URL answers 200.
            val request = GET(url, bridge.headers)
            val response = bridge.client.newCall(request).execute()
            response.code shouldBe 200
            response.body.string() shouldBe "cover-bytes"
            response.close()
        } finally {
            server.shutdown()
        }
    }
}
