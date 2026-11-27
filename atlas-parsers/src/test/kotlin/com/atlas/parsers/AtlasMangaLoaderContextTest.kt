package com.atlas.parsers

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.config.ConfigKey

class AtlasMangaLoaderContextTest {

    private val context = AtlasMangaLoaderContext()

    @Test
    fun `default user agent is a browser agent`() {
        assertTrue(context.getDefaultUserAgent().startsWith("Mozilla/5.0"))
    }

    @Test
    fun `config returns key defaults without overrides`() {
        val config = context.getConfig(org.koitharu.kotatsu.parsers.model.MangaParserSource.MANGADEX)
        assertEquals("first.example", config[ConfigKey.Domain("first.example", "second.example")])
        assertEquals("ua", config[ConfigKey.UserAgent("ua")])
    }

    @Test
    fun `config overrides win over defaults`() {
        val ctx = AtlasMangaLoaderContext(
            configOverrides = mapOf(
                "MANGADEX" to mapOf("domain" to "custom.example"),
            ),
        )
        val config = ctx.getConfig(org.koitharu.kotatsu.parsers.model.MangaParserSource.MANGADEX)
        assertEquals("custom.example", config[ConfigKey.Domain("first.example")])
    }

    @Test
    fun `evaluateJs evaluates pure JS on a blank page`() {
        val result = kotlinx.coroutines.runBlocking { context.evaluateJs("1+1") }
        assertEquals("2", result)
    }

    @Test
    fun `evaluateJs returns null for a failing script instead of throwing`() {
        val result = kotlinx.coroutines.runBlocking {
            context.evaluateJs("(() => { throw new Error('kaboom'); })()")
        }
        assertEquals(null, result)
    }

    @Test
    fun `browser hooks still fail cleanly without a WebView`() {
        val parser = KotatsuParserRegistry().newParser(
            org.koitharu.kotatsu.parsers.model.MangaParserSource.MANGADEX,
            context,
        )
        assertThrows(UnsupportedOperationException::class.java) {
            context.requestBrowserAction(parser, "https://example.com/")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                context.interceptWebViewRequests("https://example.com/", "return true;", 1000L)
            }
        }
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinx.coroutines.runBlocking {
                context.captureWebViewUrls("https://example.com/", Regex("example"), 1000L)
            }
        }
    }

    @Test
    fun `createBitmap has the requested size`() {
        val bitmap = context.createBitmap(3, 2)
        assertEquals(3, bitmap.width)
        assertEquals(2, bitmap.height)
    }

    @Test
    fun `redrawImageResponse round-trips a png through the redraw`() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/i.png").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody("image/png".toMediaType()))
            .build()
        val out = context.redrawImageResponse(response) { it }
        val decoded = ImageIO.read(checkNotNull(out.body).byteStream())
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)
    }
}
