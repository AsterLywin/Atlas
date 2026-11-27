package com.atlas.reader.source.kotatsu

import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import atlas.core.common.preference.InMemoryPreferenceStore
import atlas.core.common.storage.FolderProvider
import atlas.data.Chapter
import atlas.data.Database
import atlas.data.History
import atlas.data.InstantColumnAdapter
import atlas.data.IntColumnAdapter
import atlas.data.Manga
import atlas.data.Manga_track
import atlas.data.MemoColumnAdapter
import atlas.data.StringListColumnAdapter
import atlas.data.UpdateStrategyColumnAdapter
import atlas.data.backup.RestoreRepositoryImpl
import atlas.data.category.CategoryRepositoryImpl
import atlas.data.chapter.ChapterRepositoryImpl
import atlas.data.history.HistoryRepositoryImpl
import atlas.data.manga.MangaRepositoryImpl
import atlas.data.track.TrackRepositoryImpl
import atlas.domain.backup.repository.RestoreRepository
import atlas.domain.category.interactor.GetCategories
import atlas.domain.category.repository.CategoryRepository
import atlas.domain.chapter.interactor.GetChaptersByMangaId
import atlas.domain.chapter.interactor.ShouldUpdateDbChapter
import atlas.domain.chapter.repository.ChapterRepository
import atlas.domain.download.service.DownloadPreferences
import atlas.domain.history.repository.HistoryRepository
import atlas.domain.library.service.LibraryPreferences
import atlas.domain.manga.interactor.FetchInterval
import atlas.domain.manga.interactor.NetworkToLocalManga
import atlas.domain.manga.repository.MangaRepository
import atlas.domain.source.interactor.UpdateMangaFromRemote
import atlas.domain.source.model.StubSource
import atlas.domain.source.repository.StubSourceRepository
import atlas.domain.storage.service.StorageManager
import atlas.domain.storage.service.StoragePreferences
import atlas.domain.track.repository.TrackRepository
import com.atlas.domain.chapter.interactor.SyncChaptersWithSource
import com.atlas.domain.manga.interactor.GetExcludedScanlators
import com.atlas.domain.manga.interactor.UpdateManga
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.reader.data.download.DownloadManager
import com.atlas.reader.data.download.DownloadProvider
import com.atlas.reader.data.download.Downloader
import com.atlas.reader.extension.ExtensionManager
import com.atlas.reader.source.AndroidSourceManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.HttpUrl
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.ParsersApi
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import java.io.File
import org.koitharu.kotatsu.parsers.model.Manga as KotatsuManga

/**
 * Shared harness for the Phase 2 loop tests: a real SQLDelight database
 * (JDBC driver, in-memory — the app's AndroidX driver needs a native
 * SQLite library that Robolectric cannot load), the real repositories
 * built on it, and the real library/update/download interactors wired
 * to the real [AndroidSourceManager] with a fake Kotatsu bundle.
 *
 * The database is real, so the backup/restore round-trip goes through
 * the real queries; only the network (mockk parser) and the Android
 * plumbing around downloads (cache, notifier, store) are faked.
 */
internal data class TestStores(
    val database: Database,
    val mangaRepository: MangaRepository,
    val chapterRepository: ChapterRepository,
    val categoryRepository: CategoryRepository,
    val historyRepository: HistoryRepository,
    val trackRepository: TrackRepository,
    val restoreRepository: RestoreRepository,
    val libraryPreferences: LibraryPreferences,
    val fetchInterval: FetchInterval,
    val updateManga: UpdateManga,
    val getCategories: GetCategories,
)

internal suspend fun newTestStores(): TestStores {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    Database.Schema.create(driver).await()
    val database = Database(
        driver = driver,
        historyAdapter = History.Adapter(
            read_atAdapter = InstantColumnAdapter,
        ),
        mangaAdapter = Manga.Adapter(
            remote_genreAdapter = StringListColumnAdapter,
            remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
            remote_memoAdapter = MemoColumnAdapter,
            user_favorite_atAdapter = InstantColumnAdapter,
            state_chapter_last_updateAdapter = InstantColumnAdapter,
            state_chapter_next_updateAdapter = InstantColumnAdapter,
            state_cover_last_modifiedAdapter = InstantColumnAdapter,
        ),
        chapterAdapter = Chapter.Adapter(
            remote_date_uploadAdapter = InstantColumnAdapter,
            remote_memoAdapter = MemoColumnAdapter,
            user_last_page_readAdapter = IntColumnAdapter,
            state_date_fetchAdapter = InstantColumnAdapter,
        ),
        manga_trackAdapter = Manga_track.Adapter(
            start_dateAdapter = InstantColumnAdapter,
            finish_dateAdapter = InstantColumnAdapter,
        ),
    )
    val mangaRepository = MangaRepositoryImpl(database)
    val chapterRepository = ChapterRepositoryImpl(database)
    val categoryRepository = CategoryRepositoryImpl(database)
    val historyRepository = HistoryRepositoryImpl(database)
    val trackRepository = TrackRepositoryImpl(database)
    val restoreRepository = RestoreRepositoryImpl(
        database,
        mangaRepository,
        chapterRepository,
        trackRepository,
    )
    val libraryPreferences = LibraryPreferences(InMemoryPreferenceStore())
    val fetchInterval = FetchInterval(GetChaptersByMangaId(chapterRepository))
    return TestStores(
        database = database,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        categoryRepository = categoryRepository,
        historyRepository = historyRepository,
        trackRepository = trackRepository,
        restoreRepository = restoreRepository,
        libraryPreferences = libraryPreferences,
        fetchInterval = fetchInterval,
        updateManga = UpdateManga(mangaRepository, fetchInterval),
        getCategories = GetCategories(categoryRepository),
    )
}

internal class FakeParsersBundle(private val parser: MangaParser) : ParsersBundle {
    override val apiVersion: Int = ParsersApi.VERSION
    override val version: Long = 1L
    override val revision: String = "test"
    override val sources: List<ParserMangaSource> = listOf(FakeSource())
    override fun findSource(name: String): ParserMangaSource? =
        sources.find { it.name == name }

    override fun newParser(source: ParserMangaSource, context: MangaLoaderContext): MangaParser =
        parser

    override suspend fun resolveLink(context: MangaLoaderContext, link: HttpUrl): KotatsuManga? = null
}

internal class FakeStubSourceRepository : StubSourceRepository {
    private val store = mutableMapOf<Long, StubSource>()
    private val flow = MutableStateFlow<List<StubSource>>(emptyList())
    override fun subscribeAll(): Flow<List<StubSource>> = flow
    override suspend fun getStubSource(id: Long): StubSource? = store[id]
    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        store[id] = StubSource(id, lang, name)
        flow.value = store.values.toList()
    }
}

/** A real [AndroidSourceManager] serving one fake Kotatsu source. */
internal fun fakeKotatsuManager(
    scope: CoroutineScope,
    parser: MangaParser,
    loaderContext: MangaLoaderContext,
    stubSourceRepository: StubSourceRepository = FakeStubSourceRepository(),
    downloadManager: Lazy<DownloadManager> = lazyOf(mockk(relaxed = true)),
): Pair<AndroidSourceManager, KotatsuHttpSource> {
    val registry = KotatsuParserRegistry(FakeParsersBundle(parser))
    val kotatsuSources = KotatsuSourceProvider().buildSources(loaderContext, registry)
    val manager = AndroidSourceManager(
        scope = scope,
        extensionManager = mockk<ExtensionManager> {
            every { loadedExtensionsFlow } returns flowOf(emptyList())
        },
        sourceRepository = stubSourceRepository,
        localSource = mockk(relaxed = true),
        downloadManager = downloadManager,
        kotatsuSourceProvider = mockk {
            every { sources } returns kotatsuSources
        },
    )
    return manager to kotatsuSources.single()
}

/** The real download stack, with only the Android plumbing faked. */
internal fun realDownloadManager(
    scope: CoroutineScope,
    context: Context,
    stores: TestStores,
    sourceManager: atlas.domain.source.service.SourceManager,
): Pair<DownloadManager, Downloader> {
    val folderProvider = mockk<FolderProvider> {
        val dir = File(System.getProperty("java.io.tmpdir"), "atlas-test-downloads")
            .also { it.mkdirs() }
        every { directory() } returns dir
        every { path() } returns dir.absolutePath
    }
    val storagePreferences = StoragePreferences(folderProvider, InMemoryPreferenceStore())
    val storageManager = StorageManager(scope, context, storagePreferences)
    val provider = DownloadProvider(context, storageManager, stores.libraryPreferences)
    val downloader = Downloader(
        scope = scope,
        context = context,
        provider = provider,
        cache = mockk(relaxed = true),
        sourceManager = sourceManager,
        chapterCache = mockk(relaxed = true),
        downloadPreferences = mockk(relaxed = true),
        xml = mockk<XML>(relaxed = true),
        getCategories = stores.getCategories,
        getTracks = mockk(relaxed = true),
        store = mockk(relaxed = true),
        notifier = mockk(relaxed = true),
    )
    val manager = DownloadManager(
        context = context,
        provider = provider,
        cache = mockk(relaxed = true),
        getCategories = stores.getCategories,
        getManga = mockk(relaxed = true),
        getChapter = mockk(relaxed = true),
        sourceManager = sourceManager,
        downloadPreferences = mockk(relaxed = true),
        downloader = downloader,
        pendingDeleter = mockk(relaxed = true),
    )
    return manager to downloader
}

/** The real remote-update interactor, wired to the given stores. */
internal fun TestStores.newUpdateMangaFromRemote(
    sourceManager: atlas.domain.source.service.SourceManager,
    downloadManager: DownloadManager,
): UpdateMangaFromRemote {
    val sync = SyncChaptersWithSource(
        downloadManager = downloadManager,
        downloadProvider = mockk(relaxed = true),
        chapterRepository = chapterRepository,
        shouldUpdateDbChapter = ShouldUpdateDbChapter(),
        updateManga = updateManga,
        getExcludedScanlators = GetExcludedScanlators(mangaRepository),
        libraryPreferences = libraryPreferences,
    )
    return UpdateMangaFromRemote(
        sourceManager = sourceManager,
        chapterRepository = chapterRepository,
        mangaRepository = mangaRepository,
        syncChaptersWithSource = sync,
        coverCache = mockk(relaxed = true),
        libraryPreferences = libraryPreferences,
        downloadManager = downloadManager,
    )
}

internal fun TestStores.newNetworkToLocalManga() = NetworkToLocalManga(mangaRepository)
