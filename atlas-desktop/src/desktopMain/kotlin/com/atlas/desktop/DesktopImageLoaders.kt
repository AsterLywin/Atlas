package com.atlas.desktop

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.atlas.core.library.Book
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import okio.Path.Companion.toPath

/**
 * Coil 3 [ImageLoader]s for the desktop, one per source.
 *
 * The network layer is the parser host's own [OkHttpClient] (see
 * [buildParserClient]): the parser's interceptor chain plus its request
 * headers, on the shared cookie jar.
 *
 * Covers are cached on disk under [cacheDir] (512 MB cap) so a
 * relaunch does not re-download the library grid.
 */
class DesktopImageLoaders(
    private val loaderContext: AtlasMangaLoaderContext,
    private val registry: KotatsuParserRegistry,
    private val cacheDir: File,
) {
    private val loaders = ConcurrentHashMap<String, ImageLoader>()

    /** The loader for [book]'s source (falls back to the base client for unknown sources). */
    fun forBook(book: Book): ImageLoader = forSource(book.sourceId)

    fun forSource(sourceId: String): ImageLoader =
        loaders.getOrPut(sourceId) { buildLoader(sourceId) }

    private fun buildLoader(sourceId: String): ImageLoader {
        val parser = sourceId.removePrefix("kotatsu:").let { name ->
            registry.findSource(name)?.let { registry.newParser(it, loaderContext) }
        }
        val client = buildParserClient(loaderContext, parser)
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(OkHttpNetworkFetcherFactory({ client }))
            }
            .diskCache {
                // Sanitised: source ids contain ':' ("kotatsu:MANGADEX"),
                // which is not a valid Windows directory name.
                val dirName = sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                DiskCache.Builder()
                    .directory(cacheDir.resolve(dirName).absolutePath.toPath())
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

    companion object {
        private const val DISK_CACHE_MAX_BYTES = 512L * 1024 * 1024
        private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024 * 1024
    }
}
