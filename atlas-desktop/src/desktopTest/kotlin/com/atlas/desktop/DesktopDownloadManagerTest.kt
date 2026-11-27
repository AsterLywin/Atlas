package com.atlas.desktop

import com.atlas.desktop.download.DesktopDownloadManager
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.model.UiSource
import com.atlas.ui.state.UiDownloadState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The desktop download engine against a local [MockWebServer]: pages
 * land byte-identical, progress is reported per page, a bad page fails
 * cleanly, and cancelling removes the partial chapter. The "source"
 * here is unknown to the registry, so the manager falls back to the
 * base parser-host client — no real parser is involved.
 */
class DesktopDownloadManagerTest {

    private lateinit var tempDir: File
    private lateinit var server: MockWebServer
    private lateinit var manager: DesktopDownloadManager

    private val book = UiBook(id = "b1", title = "Fake Manga", sourceId = "kotatsu:FAKE")
    private val chapter = UiChapter(id = "c1", bookId = "b1", title = "First", number = 1f)

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-download-test-${System.nanoTime()}")
            .also { it.mkdirs() }
        server = MockWebServer()
        server.start()
        manager = DesktopDownloadManager(
            AtlasMangaLoaderContext(),
            KotatsuParserRegistry(),
            FakePageRepository(emptyMap()),
            tempDir.resolve("downloads"),
        )
    }

    @AfterEach
    fun tearDown() {
        manager.close()
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `a whole chapter downloads byte-identical with per-page progress`() = runBlocking {
        val pageBodies = listOf(
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5, 6, 7),
            byteArrayOf(8),
        )
        val urls = pageBodies.mapIndexed { index, body ->
            server.enqueue(
                MockResponse().setBody(Buffer().write(body)).addHeader("Content-Type", "image/jpeg"),
            )
            server.url("/p${index + 1}.jpg").toString()
        }
        replaceRepository(urls)

        val seen = mutableListOf<UiDownloadState>()
        val collector = launch { manager.state(book, chapter).collect { seen.add(it) } }
        try {
            manager.download(book, chapter)
            val final = awaitState { it is UiDownloadState.Downloaded }
            assertEquals(UiDownloadState.Downloaded, final)
        } finally {
            collector.cancel()
        }

        // Progress was reported per finished page: 1/3 then 2/3.
        val progresses = seen.filterIsInstance<UiDownloadState.Downloading>().map { it.progress }
        assertTrue(progresses.contains(1f / 3), "progresses were $progresses")
        assertTrue(progresses.contains(2f / 3), "progresses were $progresses")

        val dir = manager.downloadedDir(book, chapter)
        assertTrue(dir.resolve(".complete").exists(), "complete marker missing")
        pageBodies.forEachIndexed { index, body ->
            val file = dir.resolve("%03d.jpg".format(index + 1))
            assertTrue(file.exists(), "page file $file missing")
            assertTrue(file.readBytes().contentEquals(body), "page ${index + 1} bytes differ")
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a second download of a finished chapter does not hit the network`() = runBlocking {
        val body = byteArrayOf(9, 9, 9)
        server.enqueue(MockResponse().setBody(Buffer().write(body)).addHeader("Content-Type", "image/png"))
        replaceRepository(listOf(server.url("/only.png").toString()))

        manager.download(book, chapter)
        awaitState { it is UiDownloadState.Downloaded }
        assertEquals(1, server.requestCount)

        manager.download(book, chapter)
        // Give the no-op a moment to (not) run, then check nothing new arrived.
        kotlinx.coroutines.delay(300)
        assertEquals(1, server.requestCount)
        assertEquals(UiDownloadState.Downloaded, manager.state(book, chapter).value)
    }

    @Test
    fun `a failed page fails the chapter and deletes the partial files`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(Buffer().write(byteArrayOf(1))).addHeader("Content-Type", "image/jpeg"),
        )
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(
            MockResponse().setBody(Buffer().write(byteArrayOf(2))).addHeader("Content-Type", "image/jpeg"),
        )
        replaceRepository(
            listOf(
                server.url("/p1.jpg").toString(),
                server.url("/p2.jpg").toString(),
                server.url("/p3.jpg").toString(),
            ),
        )

        manager.download(book, chapter)
        val final = awaitState { it is UiDownloadState.Failed }
        assertTrue((final as UiDownloadState.Failed).reason.contains("404"))
        assertFalse(
            manager.downloadedDir(book, chapter).exists(),
            "partial chapter dir must be deleted on failure",
        )
        assertEquals(2, server.requestCount)

        // Retry works after a failure.
        server.enqueue(
            MockResponse().setBody(Buffer().write(byteArrayOf(1))).addHeader("Content-Type", "image/jpeg"),
        )
        server.enqueue(
            MockResponse().setBody(Buffer().write(byteArrayOf(2))).addHeader("Content-Type", "image/jpeg"),
        )
        server.enqueue(
            MockResponse().setBody(Buffer().write(byteArrayOf(3))).addHeader("Content-Type", "image/jpeg"),
        )
        manager.download(book, chapter)
        awaitState { it is UiDownloadState.Downloaded }
        assertEquals(UiDownloadState.Downloaded, manager.state(book, chapter).value)
    }

    @Test
    fun `cancelling mid-download removes the partial chapter`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Thread.sleep(2000)
                return MockResponse()
                    .setBody(Buffer().write(byteArrayOf(1, 2, 3)))
                    .addHeader("Content-Type", "image/jpeg")
            }
        }
        replaceRepository(
            listOf(server.url("/slow1.jpg").toString(), server.url("/slow2.jpg").toString()),
        )

        manager.download(book, chapter)
        awaitState { it is UiDownloadState.Downloading }
        manager.cancel(book, chapter)
        val final = awaitState { it is UiDownloadState.NotDownloaded }
        assertEquals(UiDownloadState.NotDownloaded, final)
        assertFalse(
            manager.downloadedDir(book, chapter).exists(),
            "partial chapter dir must be deleted on cancel",
        )
    }

    @Test
    fun `a finished chapter is still downloaded after a fresh manager opens the same dir`() =
        runBlocking {
            val body = byteArrayOf(7, 7)
            server.enqueue(
                MockResponse().setBody(Buffer().write(body)).addHeader("Content-Type", "image/jpeg"),
            )
            replaceRepository(listOf(server.url("/p1.jpg").toString()))
            manager.download(book, chapter)
            awaitState { it is UiDownloadState.Downloaded }
            manager.close()

            val fresh = DesktopDownloadManager(
                AtlasMangaLoaderContext(),
                KotatsuParserRegistry(),
                FakePageRepository(emptyMap()),
                tempDir.resolve("downloads"),
            )
            try {
                assertEquals(UiDownloadState.Downloaded, fresh.state(book, chapter).value)
            } finally {
                fresh.close()
            }
        }

    @Test
    fun `an unknown chapter starts as not downloaded`() {
        assertEquals(UiDownloadState.NotDownloaded, manager.state(book, chapter).value)
    }

    private suspend fun awaitState(
        predicate: (UiDownloadState) -> Boolean,
    ): UiDownloadState = withTimeout(15_000) {
        manager.state(book, chapter).first { predicate(it) }
    }

    private fun replaceRepository(pageUrls: List<String>) {
        manager.close()
        manager = DesktopDownloadManager(
            AtlasMangaLoaderContext(),
            KotatsuParserRegistry(),
            FakePageRepository(mapOf(chapter.id to pageUrls)),
            tempDir.resolve("downloads"),
        )
    }

    /** An [AtlasRepository] that only serves page lists; everything else is empty. */
    private class FakePageRepository(
        private val pagesByChapter: Map<String, List<String>>,
    ) : AtlasRepository {
        override suspend fun listLibraryBooks(): List<UiBook> = emptyList()
        override suspend fun listLibraryChapters(bookId: String): List<UiChapter> = emptyList()
        override suspend fun getLibraryBook(bookId: String): UiBook? = null
        override suspend fun addToLibrary(book: UiBook, chapters: List<UiChapter>) = Unit
        override suspend fun removeFromLibrary(bookId: String) = Unit
        override fun listSources(): List<UiSource> = emptyList()
        override suspend fun browseBooks(sourceId: String): List<UiBook> = emptyList()
        override suspend fun searchBooks(sourceId: String, query: String): List<UiBook> = emptyList()
        override suspend fun loadChapters(book: UiBook): List<UiChapter> = emptyList()
        override suspend fun loadPages(chapterId: String): List<String> =
            pagesByChapter[chapterId].orEmpty()
    }
}
