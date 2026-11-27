package com.atlas.reader.source.kotatsu

import android.app.Application
import android.net.Uri
import atlas.domain.manga.model.Manga
import atlas.domain.source.interactor.UpdateMangaFromRemote
import atlas.domain.source.repository.StubSourceRepository
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.reader.data.backup.BackupDecoder
import com.atlas.reader.data.backup.BackupNotifier
import com.atlas.reader.data.backup.BackupReader
import com.atlas.reader.data.backup.BackupWriter
import com.atlas.reader.data.backup.create.BackupOptions
import com.atlas.reader.data.backup.create.creators.MangaBackupCreator
import com.atlas.reader.data.backup.create.creators.SourcesBackupCreator
import com.atlas.reader.data.backup.models.Backup
import com.atlas.reader.data.backup.models.BackupChapter
import com.atlas.reader.data.backup.models.BackupManga
import com.atlas.reader.data.backup.models.BackupSource
import com.atlas.reader.data.backup.restore.BackupRestorer
import com.atlas.reader.data.backup.restore.RestoreOptions
import com.atlas.reader.data.backup.restore.restorers.CategoriesRestorer
import com.atlas.reader.data.backup.restore.restorers.ExtensionStoreRestorer
import com.atlas.reader.data.backup.restore.restorers.MangaRestorer
import com.atlas.reader.data.backup.restore.restorers.PreferenceRestorer
import com.atlas.reader.data.download.DownloadManager
import com.atlas.reader.data.download.Downloader
import com.atlas.reader.source.AndroidSourceManager
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Headers
import okhttp3.Interceptor
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.koitharu.kotatsu.parsers.model.Manga as KotatsuManga

/**
 * End-to-end loop tests (Phase 2, tasks 2–4a) on a real SQLDelight
 * database (JDBC driver, in-memory) with the real repositories and
 * the real library/update/download interactors, against one fake
 * Kotatsu source served through the real [AndroidSourceManager].
 *
 * The network is faked (mockk parser — no live HTTP; real OkHttp
 * cannot execute under Robolectric, see [KotatsuRegistrationTest]),
 * but everything below the source — insert, update, chapter sync,
 * download queue, backup write, restore into a fresh database — is
 * the production code path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class KotatsuLibraryLoopTest {

    private data class World(
        val context: Application,
        val stores: TestStores,
        val manager: AndroidSourceManager,
        val bridge: KotatsuHttpSource,
        val downloadManager: DownloadManager,
        val downloader: Downloader,
        val stubSourceRepository: StubSourceRepository,
        val updater: UpdateMangaFromRemote,
    )

    @Test
    fun `full loop - library update download backup restore update again`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val world = buildWorldInTest(
                mapOf(
                    1 to listOf(11L to 1f),
                    2 to listOf(11L to 1f, 12L to 1.5f),
                ),
            )
            val stores = world.stores

            // 1. Browse the source and add the manga to the library.
            val smanga = world.bridge.getPopularManga(1).mangas.single()
            val inserted = stores.newNetworkToLocalManga()(
                Manga.create().copy(
                    source = world.bridge.id,
                    url = smanga.url,
                    title = smanga.title,
                    thumbnailUrl = smanga.thumbnail_url,
                    author = smanga.author,
                    description = smanga.description,
                    status = smanga.status.toLong(),
                ),
            )
            stores.updateManga.awaitUpdateFavorite(inserted.id, true)
            val libraryManga = stores.mangaRepository.getMangaById(inserted.id)
            libraryManga.favorite shouldBe true

            // 2. First update: chapter 1 appears.
            val first = world.updater(libraryManga, fetchDetails = true, fetchChapters = true)
            first.isSuccess shouldBe true
            first.getOrThrow().newChapters shouldHaveSize 1
            var chapters = stores.chapterRepository.getChapterByMangaId(inserted.id)
            chapters shouldHaveSize 1
            chapters.single().chapterNumber shouldBe 1.0

            // 3. Second update: chapter 1.5 appears.
            val second = world.updater(
                stores.mangaRepository.getMangaById(inserted.id),
                fetchDetails = true,
                fetchChapters = true,
            )
            second.isSuccess shouldBe true
            second.getOrThrow().newChapters shouldHaveSize 1
            second.getOrThrow().newChapters.single().chapterNumber shouldBe 1.5
            chapters = stores.chapterRepository.getChapterByMangaId(inserted.id)
            chapters shouldHaveSize 2

            // 4. Queue the new chapter for download.
            val newChapter = chapters.single { it.chapterNumber == 1.5 }
            world.downloadManager.downloadChapters(
                stores.mangaRepository.getMangaById(inserted.id),
                listOf(newChapter),
                autoStart = false,
            )
            world.downloadManager.queueState.value shouldHaveSize 1
            world.downloadManager.queueState.value.single().chapter.id shouldBe newChapter.id

            // 5. Back up the library (real creators, real stream format).
            val backupMangas = MangaBackupCreator(
                stores.mangaRepository,
                stores.chapterRepository,
                stores.categoryRepository,
                stores.historyRepository,
                stores.trackRepository,
            )(
                listOf(stores.mangaRepository.getMangaById(inserted.id)),
                BackupOptions(),
            ).toList()
            backupMangas shouldHaveSize 1
            backupMangas.single().favorite shouldBe true
            backupMangas.single().chapters shouldHaveSize 2
            val backupSources = SourcesBackupCreator(world.manager)(setOf(world.bridge.id))
            backupSources.single().name shouldBe "Fake Source"
            val sink = Buffer()
            val writer = BackupWriter(sink, ProtoBuf)
            backupMangas.forEach(writer::writeEntry)
            writer.writeMetadata(Backup(emptyList(), backupSources = backupSources))
            val bytes = sink.readByteArray()

            // 6. Restore into a fresh database.
            val fresh = newTestStores()
            val entries = BackupReader(Buffer().write(bytes), ProtoBuf).let { reader ->
                buildList {
                    while (reader.nextEntry()) add(reader.readEntry())
                }
            }
            entries shouldHaveSize 1
            MangaRestorer(fresh.restoreRepository, fresh.getCategories, fresh.fetchInterval)
                .restore(entries, emptyList())
            val restored = fresh.mangaRepository.getMangaByUrlAndSourceId(smanga.url, world.bridge.id)
            restored shouldNotBe null
            restored!!.favorite shouldBe true
            restored.title shouldBe "Fake Manga"
            val restoredChapters = fresh.chapterRepository.getChapterByMangaId(restored.id)
            restoredChapters shouldHaveSize 2
            restoredChapters.map { it.chapterNumber } shouldContain 1.5
            restoredChapters.single { it.chapterNumber == 1.5 }.scanlator shouldBe "Scan Team"

            // 7. Update again on the restored manga: works, nothing new.
            val freshUpdater = fresh.newUpdateMangaFromRemote(world.manager, world.downloadManager)
            val third = freshUpdater(restored, fetchDetails = true, fetchChapters = true)
            third.isSuccess shouldBe true
            third.getOrThrow().newChapters shouldHaveSize 0
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `old mihon backup restores unknown source as stub`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val world = buildWorldInTest(
                mapOf(1 to listOf(11L to 1f)),
            )
            val fresh = world.stores

            // A .tachibk file as old Mihon would write it: a manga from
            // an extension source this install does not have.
            val oldManga = BackupManga(
                source = 999999L,
                url = "/manga/old",
                title = "Old Mihon Manga",
                chapters = listOf(
                    BackupChapter(url = "/chapter/1", name = "Chapter 1", chapterNumber = 1f),
                ),
            )
            val sink = Buffer()
            val writer = BackupWriter(sink, ProtoBuf)
            writer.writeEntry(oldManga)
            writer.writeMetadata(
                Backup(
                    emptyList(),
                    backupSources = listOf(BackupSource(name = "Old Extension", sourceId = 999999L)),
                ),
            )
            val file = File.createTempFile("old-mihon-", ".tachibk")
                .also { it.writeBytes(sink.readByteArray()) }

            val restorer = BackupRestorer(
                notifier = mockk<BackupNotifier>(relaxed = true),
                isSync = false,
                context = world.context,
                downloadCache = mockk(relaxed = true),
                categoriesRestorer = CategoriesRestorer(
                    fresh.categoryRepository,
                    fresh.getCategories,
                    fresh.libraryPreferences,
                ),
                preferenceRestorer = PreferenceRestorer(
                    world.context,
                    fresh.getCategories,
                    atlas.core.common.preference.InMemoryPreferenceStore(),
                ),
                extensionStoreRestorer = ExtensionStoreRestorer(mockk(relaxed = true)),
                mangaRestorer = MangaRestorer(
                    fresh.restoreRepository,
                    fresh.getCategories,
                    fresh.fetchInterval,
                ),
                backupDecoder = BackupDecoder(world.context, ProtoBuf),
                sourceManager = world.manager,
                stubSourceRepository = world.stubSourceRepository,
            )
            restorer.restore(
                Uri.fromFile(file),
                RestoreOptions(
                    libraryEntries = true,
                    categories = false,
                    appSettings = false,
                    extensionStores = false,
                    sourceSettings = false,
                ),
            )

            // The manga is in the library and its source is a stub.
            val restoredOld = fresh.mangaRepository.getMangaByUrlAndSourceId("/manga/old", 999999L)
            restoredOld shouldNotBe null
            restoredOld!!.title shouldBe "Old Mihon Manga"
            val stub = world.stubSourceRepository.getStubSource(999999L)
            stub shouldNotBe null
            stub!!.name shouldBe "Old Extension"

            // Updating the stubbed manga fails, but the Kotatsu manga
            // still updates fine — one bad source does not poison the rest.
            val unknownUpdate = world.updater(restoredOld, fetchDetails = true, fetchChapters = true)
            unknownUpdate.isFailure shouldBe true

            val smanga = world.bridge.getPopularManga(1).mangas.single()
            val inserted = fresh.newNetworkToLocalManga()(
                Manga.create().copy(
                    source = world.bridge.id,
                    url = smanga.url,
                    title = smanga.title,
                ),
            )
            val knownUpdate = world.updater(
                fresh.mangaRepository.getMangaById(inserted.id),
                fetchDetails = true,
                fetchChapters = true,
            )
            knownUpdate.isSuccess shouldBe true
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `two mangas of one source update concurrently`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            // Two mangas from the same fake parser; getDetails answers
            // per manga id (the bridge rebuilds the manga with its id).
            val world = buildWorldInTest(
                mapOf(1 to listOf(11L to 1f)),
                secondMangaChapters = listOf(21L to 1f),
            )
            val stores = world.stores
            val bridge = world.bridge

            val firstManga = insertManga(stores, bridge, 1L, "Fake Manga")
            val secondManga = insertManga(stores, bridge, 2L, "Second Manga")

            val first = async {
                world.updater(
                    stores.mangaRepository.getMangaById(firstManga.id),
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
            val second = async {
                world.updater(
                    stores.mangaRepository.getMangaById(secondManga.id),
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
            val firstResult = first.await()
            val secondResult = second.await()
            firstResult.isSuccess shouldBe true
            secondResult.isSuccess shouldBe true
            firstResult.getOrThrow().newChapters shouldHaveSize 1
            secondResult.getOrThrow().newChapters shouldHaveSize 1
            stores.chapterRepository.getChapterByMangaId(firstManga.id) shouldHaveSize 1
            stores.chapterRepository.getChapterByMangaId(secondManga.id) shouldHaveSize 1
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun insertManga(
        stores: TestStores,
        bridge: KotatsuHttpSource,
        mangaId: Long,
        title: String,
    ): Manga {
        val smanga = bridge.getPopularManga(1).mangas.single()
        // The popular list only has manga 1; rebuild the URL for the
        // second manga from its own id so getDetails can tell them apart.
        val url = if (mangaId == 1L) {
            smanga.url
        } else {
            com.atlas.parsers.KotatsuIds.bookIdOf(
                FakeSource(),
                fakeManga(mangaId, title, FakeSource()),
            )
        }
        return stores.newNetworkToLocalManga()(
            Manga.create().copy(
                source = bridge.id,
                url = url,
                title = title,
            ),
        )
    }

    /**
     * buildWorld needs a suspend database setup, so tests call this
     * suspend version instead of the plain [buildWorld] sketch above.
     */
    private suspend fun TestScope.buildWorldInTest(
        detailsChapters: Map<Int, List<Pair<Long, Float>>>,
        secondMangaChapters: List<Pair<Long, Float>>? = null,
    ): World {
        val context: Application = RuntimeEnvironment.getApplication()
        val stores = newTestStores()
        val source = FakeSource()
        val detailsCalls = AtomicInteger(0)
        val parser = mockk<MangaParser> {
            every { this@mockk.source } returns source
            every { availableSortOrders } returns
                setOf(SortOrder.UPDATED, SortOrder.POPULARITY, SortOrder.RELEVANCE)
            every { domain } returns "fake.example"
            every { getRequestHeaders() } returns Headers.Builder()
                .add("User-Agent", "test-agent")
                .build()
            every { intercept(any()) } answers {
                val chain = firstArg<Interceptor.Chain>()
                chain.proceed(chain.request())
            }
            coEvery { getList(any(), any(), any()) } returns
                listOf(fakeManga(1, "Fake Manga", source))
            coEvery { getDetails(any()) } answers {
                val manga = firstArg<KotatsuManga>()
                val call = detailsCalls.incrementAndGet()
                val chapters = if (secondMangaChapters != null && manga.id == 2L) {
                    secondMangaChapters
                } else {
                    detailsChapters[call] ?: detailsChapters.values.last()
                }
                fakeManga(manga.id, if (manga.id == 2L) "Second Manga" else "Fake Manga", source)
                    .copy(chapters = chapters.map { (id, number) -> fakeChapter(id, number, source) })
            }
            coEvery { getPages(any()) } returns
                listOf(fakePage(101, "/img/1", source))
            coEvery { getPageUrl(any()) } returns "https://img.fake.example/img.png"
        }
        val loaderContext = AndroidMangaLoaderContext(httpClient = mockk())
        lateinit var realDownloadManager: DownloadManager
        val stubSourceRepository = FakeStubSourceRepository()
        val (manager, bridge) = fakeKotatsuManager(
            scope = backgroundScope,
            parser = parser,
            loaderContext = loaderContext,
            stubSourceRepository = stubSourceRepository,
            downloadManager = lazy { realDownloadManager },
        )
        val (downloadManager, downloader) = realDownloadManager(
            scope = backgroundScope,
            context = context,
            stores = stores,
            sourceManager = manager,
        )
        realDownloadManager = downloadManager
        val updater = stores.newUpdateMangaFromRemote(manager, downloadManager)
        return World(
            context = context,
            stores = stores,
            manager = manager,
            bridge = bridge,
            downloadManager = downloadManager,
            downloader = downloader,
            stubSourceRepository = stubSourceRepository,
            updater = updater,
        )
    }
}
