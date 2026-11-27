package com.atlas.desktop

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import com.atlas.desktop.backup.DesktopBackupCreator
import com.atlas.desktop.backup.DesktopBackupRestorer
import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.desktop.download.DesktopDownloadManager
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.parsers.KotatsuSourceAdapter
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.data.resolveReaderPages
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.model.UiSource
import com.atlas.ui.state.ReadingProgress
import com.atlas.ui.state.UiDownloadState
import java.io.File
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.Favicons
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQueryCapabilities

/**
 * The whole desktop loop against a fake source on a local
 * [MockWebServer], no Robolectric, no real network:
 *
 * source (list/search/chapters/pages) -> library (real SQLDelight
 * store) -> reader (pages resolve to URLs, then to local files once
 * downloaded) -> download (files on disk, byte-identical) ->
 * backup (.tachibk) -> restore into a fresh database -> the library
 * comes back whole.
 *
 * The "reader" leg is the headless half of the real reader: page
 * resolution ([resolveReaderPages]) plus a real Coil load of every
 * resolved page — the same load the composable performs — so an
 * offline chapter provably renders without touching the network.
 */
class DesktopEndToEndTest {

    private lateinit var tempDir: File
    private lateinit var server: MockWebServer
    private lateinit var store: DesktopLibraryStore
    private lateinit var repository: DesktopAtlasRepository
    private lateinit var downloadManager: DesktopDownloadManager

    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-e2e-test-${System.nanoTime()}")
            .also { it.mkdirs() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when {
                    request.path == "/list" || request.path?.startsWith("/search") == true ->
                        MockResponse().setBody("1|Fake Manga|Fake Author\n")

                    request.path?.matches(Regex("/p\\d+\\.jpg")) == true ->
                        MockResponse().setBody(Buffer().write(png))
                            .addHeader("Content-Type", "image/png")

                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()

        val loaderContext = AtlasMangaLoaderContext()
        val registry = KotatsuParserRegistry()
        val adapter = KotatsuSourceAdapter(FakeE2EParser(server, loaderContext), loaderContext)
        store = DesktopLibraryStore.open(tempDir.resolve("atlas.db"))
        repository = DesktopAtlasRepository(registry, loaderContext, store)
        // The repository's own loadPages needs a registry adapter, and
        // the fake source is not in the registry — so the download
        // manager reads pages through the same adapter the test browses
        // with. Everything else is the real repository.
        val pageRepository = object : AtlasRepository by repository {
            override suspend fun loadPages(chapterId: String): List<String> =
                adapter.listPages(chapterId)
        }
        downloadManager = DesktopDownloadManager(
            loaderContext,
            registry,
            pageRepository,
            tempDir.resolve("downloads"),
        )
        this.adapter = adapter
        this.pageRepository = pageRepository
        this.imageLoaders = DesktopImageLoaders(loaderContext, registry, tempDir.resolve("image-cache"))
    }

    private lateinit var adapter: KotatsuSourceAdapter
    private lateinit var pageRepository: AtlasRepository
    private lateinit var imageLoaders: DesktopImageLoaders

    @AfterEach
    fun tearDown() {
        downloadManager.close()
        store.close()
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `source to library to reader to download to backup and back`() = runBlocking {
        // 1. Source: list, search, chapters, pages — all from the fake.
        val books = adapter.listBooks()
        assertEquals(listOf("Fake Manga"), books.map { it.title })
        assertEquals("Fake Author", books[0].author)
        assertEquals("kotatsu:MANGADEX", books[0].sourceId)
        assertEquals(listOf("Fake Manga"), adapter.searchBooks("fake").map { it.title })

        val coreChapters = adapter.listChapters(books[0].id)
        assertEquals(listOf(1f, 2f, 2.5f), coreChapters.map { it.number })
        assertEquals("Half", coreChapters[2].title)
        val corePages = adapter.listPages(coreChapters[0].id)
        assertEquals(3, corePages.size)
        assertTrue(corePages.all { it.startsWith(server.url("/").toString()) }, "pages were $corePages")

        // 2. Library: the book and its chapters land in the real store.
        val book = books[0].toUi()
        val chapters = coreChapters.map { it.toUi() }
        repository.addToLibrary(book, chapters)
        assertEquals(listOf("Fake Manga"), repository.listLibraryBooks().map { it.title })
        assertEquals(
            listOf(1f, 2f, 2.5f),
            repository.listLibraryChapters(book.id).map { it.number },
        )

        // 3. Reader, online: pages resolve to the source URLs and every
        // one loads through the real Coil loader.
        val chapter = chapters[0]
        assertNull(downloadManager.localPages(book, chapter))
        val onlinePages = resolveReaderPages(book, chapter, pageRepository, downloadManager)
        assertEquals(corePages, onlinePages)
        onlinePages.forEach { assertPageLoads(it) }

        // 4. Reader progress: a page turn persists, and reads back.
        val progressStore = DesktopProgressStore(store)
        progressStore.saveProgress(ReadingProgress(book.id, chapter.id, 2f / 3))
        assertEquals(2f / 3, progressStore.loadProgress(book.id, chapter.id)?.fraction)

        // 5. Download: the chapter lands byte-identical on disk.
        downloadManager.download(book, chapter)
        withTimeout(15_000) {
            downloadManager.state(book, chapter).first { it is UiDownloadState.Downloaded }
        }
        val dir = downloadManager.downloadedDir(book, chapter)
        assertTrue(dir.resolve(".complete").exists(), "complete marker missing")
        (1..3).forEach { index ->
            // The file extension comes from the page URL (/p100.jpg),
            // even though the bytes are a PNG — the name is only a name.
            val file = dir.resolve("%03d.jpg".format(index))
            assertTrue(file.exists(), "page file $file missing")
            assertTrue(file.readBytes().contentEquals(png), "page $index bytes differ")
        }

        // 6. Reader, offline: pages now resolve to local files, every
        // one loads through the real Coil loader, and the server sees
        // no new page request — the chapter reads without a network.
        val localPages = downloadManager.localPages(book, chapter)
        assertEquals(3, localPages?.size)
        assertTrue(localPages!!.all { it.startsWith("file:") }, "local pages were $localPages")
        val requestsBeforeOfflineRead = server.requestCount
        val offlinePages = resolveReaderPages(book, chapter, pageRepository, downloadManager)
        assertEquals(localPages, offlinePages)
        offlinePages.forEach { assertPageLoads(it) }
        assertEquals(
            requestsBeforeOfflineRead,
            server.requestCount,
            "offline read must not hit the network",
        )

        // A chapter that was not downloaded still falls back to URLs.
        val otherChapter = chapters[1]
        assertEquals(
            adapter.listPages(otherChapter.id),
            resolveReaderPages(book, otherChapter, pageRepository, downloadManager),
        )

        // 7. Backup + restore: the library comes back whole on a fresh DB.
        val backupFile = tempDir.resolve("atlas.tachibk")
        val registry = KotatsuParserRegistry()
        val created = DesktopBackupCreator(store, registry).createBackup(backupFile)
        assertEquals(1, created.books)
        assertTrue(backupFile.exists())

        DesktopLibraryStore.open(tempDir.resolve("fresh.db")).use { fresh ->
            val report = DesktopBackupRestorer(fresh, registry).restore(backupFile)
            assertEquals(1, report.books)
            assertEquals(3, report.chapters)
            assertTrue(report.skipped.isEmpty())
            val restored = fresh.listBooks().single()
            assertEquals("Fake Manga", restored.title)
            assertEquals("Fake Author", restored.author)
            assertEquals("kotatsu:MANGADEX", restored.sourceId)
            assertEquals(
                listOf(1f, 2f, 2.5f),
                fresh.listChapters(restored.id).map { it.number },
            )
            assertEquals("Half", fresh.listChapters(restored.id)[2].title)
        }
    }

    private suspend fun assertPageLoads(model: String) {
        val loader = imageLoaders.forSource("kotatsu:MANGADEX")
        val result = loader.execute(
            ImageRequest.Builder(PlatformContext.INSTANCE).data(model).build(),
        )
        assertTrue(result is SuccessResult, "page $model failed to load: $result")
    }

    private fun Book.toUi(): UiBook = UiBook(
        id = id,
        title = title,
        author = author,
        sourceId = sourceId,
        coverUrl = coverUrl,
    )

    private fun Chapter.toUi(): UiChapter = UiChapter(
        id = id,
        bookId = bookId,
        title = title,
        number = number,
        read = read,
        progress = progress,
    )
}

/**
 * A parser that answers from fixed data: its list endpoint is the
 * [MockWebServer], chapters are fixed, and every page URL points at
 * the server's image endpoint. Same shape as the wiring test's fake,
 * but with three pages per chapter so the reader and the downloader
 * have a real page list to work through.
 */
@Suppress("DEPRECATION")
private class FakeE2EParser(
    private val server: MockWebServer,
    private val context: MangaLoaderContext,
) : MangaParser {
    override val source = MangaParserSource.MANGADEX
    override val availableSortOrders = setOf(SortOrder.UPDATED, SortOrder.RELEVANCE)
    override val searchQueryCapabilities = MangaSearchQueryCapabilities()
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override val config: MangaSourceConfig = object : MangaSourceConfig {
        override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
    }
    override val authorizationProvider: MangaParserAuthProvider? = null
    override val configKeyDomain = ConfigKey.Domain("example.com")
    override val domain: String = "example.com"

    private val chapters = listOf(
        MangaChapter(10L, "First", 1f, 0, "/manga/1/ch-1", null, 0L, null, source),
        MangaChapter(11L, null, 2f, 0, "/manga/1/ch-2", null, 0L, null, source),
        MangaChapter(12L, "Half", 2.5f, 0, "/manga/1/ch-3", null, 0L, null, source),
    )

    private fun parseBooks(body: String): List<Manga> =
        body.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("|")
                Manga(
                    id = parts[0].toLong(),
                    title = parts[1],
                    altTitles = emptySet(),
                    url = "/manga/${parts[0]}",
                    publicUrl = "https://example.com/manga/${parts[0]}",
                    rating = 0.8f,
                    contentRating = null,
                    coverUrl = null,
                    tags = emptySet(),
                    state = null,
                    authors = setOf(parts.getOrNull(2).orEmpty()),
                    source = source,
                )
            }
            .toList()

    override suspend fun getList(query: MangaSearchQuery): List<Manga> =
        getList(offset = 0, order = SortOrder.UPDATED, filter = MangaListFilter.EMPTY)

    override suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
        val url = if (filter.query.isNullOrBlank()) {
            server.url("/list")
        } else {
            server.url("/search").newBuilder().addQueryParameter("q", filter.query).build()
        }
        val request = Request.Builder().url(url).build()
        context.httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "list request failed: ${response.code}" }
            return parseBooks(response.body.string())
        }
    }

    override suspend fun getDetails(manga: Manga): Manga = manga.copy(chapters = chapters)

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> =
        listOf(
            MangaPage(100L, "/page/100", null, source),
            MangaPage(101L, "/page/101", null, source),
            MangaPage(102L, "/page/102", null, source),
        )

    override suspend fun getPageUrl(page: MangaPage): String =
        server.url("/p${page.id}.jpg").toString()

    override suspend fun getFilterOptions(): MangaListFilterOptions = MangaListFilterOptions()
    override suspend fun getFavicons(): Favicons = Favicons(emptyList(), null)
    override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) = Unit
    override suspend fun getRelatedManga(seed: Manga): List<Manga> = emptyList()
    override fun getRequestHeaders(): Headers = Headers.headersOf()
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
