package com.atlas.reader.ui.shared

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import okhttp3.Interceptor
import okio.Path.Companion.toPath
import java.util.concurrent.ConcurrentHashMap

/**
 * Coil 3 [ImageLoader]s for the shared UI on Android, one per source —
 * the Android mirror of the desktop [com.atlas.desktop.DesktopImageLoaders]:
 * the network layer is the parser host's own [okhttp3.OkHttpClient]
 * (shared cookie jar with the parsers), extended with the parser's
 * own interceptor chain plus the parser's request headers, so covers
 * and reader pages from Referer-gated sources load.
 */
class AtlasSharedImageLoaders(
    context: Context,
    private val loaderContext: AtlasMangaLoaderContext,
    private val registry: KotatsuParserRegistry,
) {
    private val appContext = context.applicationContext
    private val loaders = ConcurrentHashMap<String, ImageLoader>()

    fun forSource(sourceId: String): ImageLoader =
        loaders.getOrPut(sourceId) { buildLoader(sourceId) }

    private fun buildLoader(sourceId: String): ImageLoader {
        val parser = sourceId.removePrefix("kotatsu:").let { name ->
            registry.findSource(name)?.let { registry.newParser(it, loaderContext) }
        }
        val client = loaderContext.httpClient.newBuilder()
            .addInterceptor(headersInterceptor(parser))
            .apply { if (parser != null) addInterceptor(parser) }
            .build()
        return ImageLoader.Builder(appContext)
            .components {
                add(OkHttpNetworkFetcherFactory({ client }))
            }
            .diskCache {
                val dirName = sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("atlas_images/$dirName").absolutePath.toPath())
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(MEMORY_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
    }

    private fun headersInterceptor(parser: org.koitharu.kotatsu.parsers.MangaParser?): Interceptor =
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

    companion object {
        private const val DISK_CACHE_MAX_BYTES = 512L * 1024 * 1024
        private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024 * 1024
    }
}
