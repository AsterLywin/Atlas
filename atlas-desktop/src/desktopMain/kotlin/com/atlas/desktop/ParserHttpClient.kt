package com.atlas.desktop

import com.atlas.parsers.AtlasMangaLoaderContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.MangaParser

/**
 * The parser host's own [OkHttpClient], extended the same way the Android
 * [KotatsuHttpSource] does it: the parser's own interceptor chain (image
 * descrambling, per-source request tweaks) plus an interceptor that adds
 * the parser's request headers (Referer and friends from
 * [MangaParser.getRequestHeaders]) — sources that gate images on Referer
 * answer 403 without them. The User-Agent falls back to the loader
 * context's default. The client shares the loader context's cookie jar
 * with the parsers, so logged-in sources stay logged in.
 *
 * Both the Coil image loaders and the chapter downloader build their
 * clients here, so a page downloads with exactly the headers a cover
 * loads with.
 */
fun buildParserClient(
    loaderContext: AtlasMangaLoaderContext,
    parser: MangaParser?,
): OkHttpClient =
    loaderContext.httpClient.newBuilder()
        .addInterceptor(headersInterceptor(loaderContext, parser))
        .apply { if (parser != null) addInterceptor(parser) }
        .build()

private fun headersInterceptor(
    loaderContext: AtlasMangaLoaderContext,
    parser: MangaParser?,
): Interceptor =
    Interceptor { chain ->
        val originalHeaders = chain.request().headers
        val request = chain.request().newBuilder().apply {
            val parserHeaders = parser?.getRequestHeaders()
            if (parserHeaders != null) {
                for (i in 0 until parserHeaders.size) {
                    val name = parserHeaders.name(i)
                    if (originalHeaders[name] == null) {
                        addHeader(name, parserHeaders.value(i))
                    }
                }
            }
            if (originalHeaders["User-Agent"] == null) {
                addHeader("User-Agent", loaderContext.getDefaultUserAgent())
            }
        }.build()
        chain.proceed(request)
    }
