package com.atlas.desktop

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The desktop Coil loader serves covers through the parser host's
 * HTTP client and caches them on disk: a second loader over the same
 * cache dir must not hit the network again. Pure JVM, the "cover"
 * is a 1x1 PNG from a local [MockWebServer].
 */
class DesktopImageLoadersTest {

    private lateinit var tempDir: File
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-desktop-img-${System.nanoTime()}")
            .also { it.mkdirs() }
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `cover loads through the loader and the second loader reads the disk cache`() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(png))
                .addHeader("Content-Type", "image/png"),
        )
        val url = server.url("/cover.png").toString()
        val context = AtlasMangaLoaderContext()
        val registry = KotatsuParserRegistry()

        val first = DesktopImageLoaders(context, registry, tempDir).forSource("local")
        val result = runBlocking {
            first.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build())
        }
        assertTrue(result is SuccessResult, "expected a decoded cover, got $result")
        val recorded = server.takeRequest()
        assertEquals("/cover.png", recorded.path)
        assertNotNull(recorded.getHeader("User-Agent"))
        assertEquals(1, server.requestCount)

        // A fresh loader (empty memory cache) over the same disk cache
        // must serve the cover without another network request.
        val second = DesktopImageLoaders(context, registry, tempDir).forSource("local")
        val cached = runBlocking {
            second.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build())
        }
        assertTrue(cached is SuccessResult, "expected a cached cover, got $cached")
        assertEquals(1, server.requestCount)
    }
}
