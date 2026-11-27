package com.atlas.parsers

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl
import org.koitharu.kotatsu.parsers.model.MangaSource

/**
 * The host side of the Kotatsu parser contract, for plain JVM (desktop).
 *
 * Everything a parser needs from its host lives here: the HTTP client, the
 * cookie jar, per-source config and the image helpers. Android overrides
 * the image/JS hooks with its own subclass (see [createBitmap] /
 * [redrawImageResponse] / [evaluateJs]): `java.awt` does not exist on
 * Android, so the Android app must supply `android.graphics.Bitmap`
 * backed implementations instead.
 *
 * JavaScript ([evaluateJs]) runs on the GraalVM JS engine in
 * [AtlasJsEngine]: the page at `baseUrl` is fetched with the context
 * HTTP client and the script runs against a small browser shim
 * (DOM/localStorage/fetch/XHR) backed by that page. It returns the
 * result string, or null when the page cannot be fetched, the script
 * fails, or the timeout runs out — it never throws for a script failure.
 *
 * What is NOT implemented here, deliberately:
 * - [requestBrowserAction] / WebView interception: the base implementations
 *   throw, which only affects the 16 parsers that need a browser.
 */
open class AtlasMangaLoaderContext(
    override val httpClient: OkHttpClient = defaultHttpClient(),
    override val cookieJar: CookieJar = InMemoryCookieJar(),
    override val parsers: ParsersBundle = ParsersBundleImpl,
    private val defaultUserAgent: String = DEFAULT_USER_AGENT,
    /** Per-source config overrides: source name -> config key -> value. */
    private val configOverrides: Map<String, Map<String, Any?>> = emptyMap(),
) : MangaLoaderContext() {

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        val overrides = configOverrides[source.name].orEmpty()
        return mangaSourceConfig { key -> overrides[key.key] ?: key.defaultValue }
    }

    override fun getDefaultUserAgent(): String = defaultUserAgent

    /** Lazily created, so hosts that override [evaluateJs] never pay for it. */
    private val jsEngine: AtlasJsEngine by lazy {
        AtlasJsEngine(httpClient, cookieJar, getDefaultUserAgent())
    }

    /**
     * Pure computation on a blank page (no DOM): the deprecated overload
     * parsers use for deobfuscation. Never throws for a script failure.
     */
    override suspend fun evaluateJs(script: String): String? =
        jsEngine.evaluateJs(null, script, SINGLE_SCRIPT_TIMEOUT_MS)

    /**
     * Runs [script] against the page at [baseUrl] (fetched with the
     * context HTTP client) with [timeout] as the total budget. Returns
     * the result string, or null when the page cannot be fetched, the
     * script fails, its promise never settles, or the timeout runs out.
     * Never throws for a script failure.
     */
    override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? =
        jsEngine.evaluateJs(baseUrl, script, timeout)

    /**
     * JVM image descrambling: decode the response into a [BufferedImage],
     * let the parser redraw it, re-encode as PNG and rebuild the response.
     * When the bytes are not a decodable image, the original bytes are
     * passed through untouched.
     */
    override fun redrawImageResponse(
        response: Response,
        redraw: (image: Bitmap) -> Bitmap,
    ): Response {
        val body = response.body ?: return response
        val bytes = body.bytes()
        val contentType = body.contentType()
        val decoded = runCatching {
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull() ?: return response.newBuilder()
            .body(bytes.toResponseBody(contentType))
            .build()
        val result = redraw(AwtBitmap(decoded))
        val out = ByteArrayOutputStream()
        val image = (result as? AwtBitmap)?.image
            ?: throw IllegalArgumentException("redraw must return an AwtBitmap on the JVM")
        ImageIO.write(image, "png", out)
        return response.newBuilder()
            .body(out.toByteArray().toResponseBody("image/png".toMediaType()))
            .build()
    }

    override fun createBitmap(width: Int, height: Int): Bitmap =
        AwtBitmap(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))

    companion object {
        /**
         * A desktop Chrome user agent: sites expect a browser UA, and a
         * library UA is blocked by some of them. Same family of string the
         * parsers themselves ship in `UserAgents.kt`.
         */
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /** Budget for the single-argument overload, which carries no timeout of its own. */
        private const val SINGLE_SCRIPT_TIMEOUT_MS = 30_000L

        fun defaultHttpClient(cookieJar: CookieJar = InMemoryCookieJar()): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

/** Minimal in-memory cookie jar: cookies live for the process lifetime. */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.getOrPut(url.host) { mutableListOf() }.apply {
            for (cookie in cookies) {
                removeAll { it.name == cookie.name }
                add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host].orEmpty().filter { it.expiresAt > now }
    }
}

/** [Bitmap] backed by `java.awt.image.BufferedImage` (JVM/desktop only). */
class AwtBitmap(val image: BufferedImage) : Bitmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    override fun drawBitmap(
        sourceBitmap: Bitmap,
        src: org.koitharu.kotatsu.parsers.bitmap.Rect,
        dst: org.koitharu.kotatsu.parsers.bitmap.Rect,
    ) {
        val srcImage = (sourceBitmap as? AwtBitmap)?.image
            ?: throw IllegalArgumentException("source must be an AwtBitmap on the JVM")
        val g = image.createGraphics()
        try {
            g.drawImage(
                srcImage,
                dst.left, dst.top, dst.right, dst.bottom,
                src.left, src.top, src.right, src.bottom,
                null,
            )
        } finally {
            g.dispose()
        }
    }
}

/** Build a [MangaSourceConfig] from a lookup: override first, else the key default. */
private fun mangaSourceConfig(block: (ConfigKey<*>) -> Any?): MangaSourceConfig =
    object : MangaSourceConfig {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: ConfigKey<T>): T = block(key) as T
    }
