package com.atlas.reader.source.kotatsu

import com.atlas.parsers.InMemoryCookieJar
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.reader.network.NetworkHelper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import uy.kohesive.injekt.injectLazy

/**
 * Builds the Kotatsu sources for the Android app.
 *
 * One [KotatsuHttpSource] is built per live (not `@Broken`) source from
 * the vendored Kotatsu registry — about a thousand lightweight objects,
 * built once and shared for the process lifetime. They are seeded into
 * [com.atlas.reader.source.AndroidSourceManager] next to `LocalSource`,
 * so the source list, the reader, the downloader and the cover fetcher
 * all see them without any other change.
 *
 * The loader context is the app's own network stack: Mihon's
 * [NetworkHelper] client (Cloudflare handling included) rebuilt with a
 * cookie jar shared between the HTTP client and the parsers, plus the
 * Android bitmap hooks for image descrambling.
 */
@Inject
@SingleIn(AppScope::class)
class KotatsuSourceProvider {

    private val networkHelper: NetworkHelper by injectLazy()

    /** All live Kotatsu sources, built once and shared. */
    val sources: List<KotatsuHttpSource> by lazy { buildSources() }

    /**
     * Builds one bridge per live source. The [loaderContext] and [registry]
     * parameters exist so tests can point the parsers at a fake bundle or
     * a local server; production always calls the no-arg overload.
     */
    fun buildSources(
        loaderContext: MangaLoaderContext = createLoaderContext(),
        registry: KotatsuParserRegistry = KotatsuParserRegistry(),
    ): List<KotatsuHttpSource> {
        return registry.sources.map { source ->
            KotatsuHttpSource(registry.newParser(source, loaderContext), loaderContext)
        }
    }

    /**
     * The Android loader context: the app's network client with a cookie
     * jar shared between the client and the parsers.
     */
    fun createLoaderContext(): MangaLoaderContext {
        val cookieJar = InMemoryCookieJar()
        val httpClient = networkHelper.client.newBuilder()
            .cookieJar(cookieJar)
            .build()
        return AndroidMangaLoaderContext(
            httpClient = httpClient,
            cookieJar = cookieJar,
        )
    }
}
