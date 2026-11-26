package com.atlas.reader.source.kotatsu

import android.app.Application
import atlas.core.common.preference.InMemoryPreferenceStore
import atlas.domain.source.model.StubSource
import atlas.domain.source.repository.SourceRepository
import atlas.domain.source.repository.StubSourceRepository
import com.atlas.domain.source.interactor.GetEnabledSources
import com.atlas.domain.source.service.SourcePreferences
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.presentation.browse.SourceUiModel
import com.atlas.reader.data.download.DownloadManager
import com.atlas.reader.extension.ExtensionManager
import com.atlas.reader.source.AndroidSourceManager
import com.atlas.reader.source.online.HttpSource
import com.atlas.reader.ui.browse.source.SourcesViewModel
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.ParsersApi
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import atlas.domain.source.model.Source as DomainSource

/**
 * End-to-end registration test: a fake Kotatsu parser served through a
 * test bundle goes through the real [KotatsuSourceProvider] and the real
 * [AndroidSourceManager], and the source shows up in the real
 * [GetEnabledSources] flow and [SourcesViewModel] state — the same path
 * the reader and the downloader use to resolve a source as [HttpSource].
 *
 * Pinned to SDK 36 (the app's targetSdk); Robolectric 4.16 supports
 * API 36. Conscrypt is off: the sandbox's Conscrypt classes come from
 * android-all without their native library, so installing the provider
 * fails with UnsatisfiedLinkError; nothing here needs TLS. The plain
 * [Application] is used instead of the manifest's `App`: its `onCreate`
 * installs the crash handler, which assumes a default uncaught-exception
 * handler exists, and Robolectric provides none.
 *
 * No live HTTP happens here on purpose: executing any OkHttp call in
 * the sandbox crashes in `Android10Platform` — OkHttp 5.5 references
 * `android.net.ssl.InvalidEchDataException`, an API 37 class that even
 * the API 36 android-all does not have (verified against the SDK
 * android.jar: absent in 36, present in 37.0/37.2). Building a real
 * `OkHttpClient` in the sandbox crashes for the same reason, so the
 * loader context gets a mocked client; the test never executes HTTP.
 * The image-fetch path (`getImage` through the parser interceptor
 * chain) is covered by a plain JVM test in [KotatsuHttpSourceTest],
 * where OkHttp falls back to the JDK platform and the request really
 * runs against a MockWebServer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class KotatsuRegistrationTest {

    private class FakeParsersBundle(private val parser: MangaParser) : ParsersBundle {
        override val apiVersion: Int = ParsersApi.VERSION
        override val version: Long = 1L
        override val revision: String = "test"
        override val sources: List<ParserMangaSource> = listOf(FakeSource())
        override fun findSource(name: String): ParserMangaSource? =
            sources.find { it.name == name }

        override fun newParser(source: ParserMangaSource, context: MangaLoaderContext): MangaParser =
            parser

        override suspend fun resolveLink(context: MangaLoaderContext, link: HttpUrl): Manga? = null
    }

    private class FakeStubSourceRepository : StubSourceRepository {
        private val store = mutableMapOf<Long, StubSource>()
        private val flow = MutableStateFlow<List<StubSource>>(emptyList())
        override fun subscribeAll(): Flow<List<StubSource>> = flow
        override suspend fun getStubSource(id: Long): StubSource? = store[id]
        override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
            store[id] = StubSource(id, lang, name)
            flow.value = store.values.toList()
        }
    }

    @Test
    fun `kotatsu source appears in the list and serves content end to end`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val imageUrl = "https://img.fake.example/img.png"
            val source = FakeSource()
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
                coEvery { getDetails(any()) } returns fakeManga(1, "Fake Manga", source).copy(
                    chapters = listOf(fakeChapter(11, 1f, source)),
                )
                coEvery { getPages(any()) } returns
                    listOf(fakePage(101, "/img/1", source))
                coEvery { getPageUrl(any()) } returns imageUrl
            }
            val loaderContext = AndroidMangaLoaderContext(httpClient = mockk())
            val registry = KotatsuParserRegistry(FakeParsersBundle(parser))
            val kotatsuSources = KotatsuSourceProvider().buildSources(loaderContext, registry)
            kotatsuSources shouldHaveSize 1

            // backgroundScope: the manager's init collectors run for the
            // app's lifetime and never complete; runTest would otherwise
            // wait for them forever (UncompletedCoroutinesError).
            val manager = AndroidSourceManager(
                scope = backgroundScope,
                extensionManager = mockk<ExtensionManager> {
                    every { loadedExtensionsFlow } returns flowOf(emptyList())
                },
                sourceRepository = FakeStubSourceRepository(),
                localSource = mockk(relaxed = true),
                downloadManager = lazyOf(mockk<DownloadManager>(relaxed = true)),
                kotatsuSourceProvider = mockk {
                    every { sources } returns kotatsuSources
                },
            )

            val all = manager.getAll()
            // The mocked LocalSource (empty name) plus the Kotatsu source.
            all shouldHaveSize 2
            all.map { it.name } shouldContain "Fake Source"

            val online = manager.getOnlineSources()
            online shouldHaveSize 1
            val bridge = online.first()
            (bridge is KotatsuHttpSource) shouldBe true

            val popular = bridge.getPopularManga(1)
            popular.mangas shouldHaveSize 1
            popular.mangas.first().title shouldBe "Fake Manga"

            val update = bridge.getMangaUpdate(
                popular.mangas.first(),
                emptyList(),
                fetchDetails = true,
                fetchChapters = true,
            )
            update.chapters shouldHaveSize 1
            val pages = bridge.getPageList(update.chapters.first())
            pages shouldHaveSize 1
            pages.first().imageUrl shouldBe imageUrl

            val repository = mockk<SourceRepository> {
                every { getSources() } returns manager.sources.map { sources ->
                    sources.map {
                        DomainSource(it.id, it.lang, it.name, it.supportsLatest, false)
                    }
                }
            }
            val preferences = SourcePreferences(InMemoryPreferenceStore())
            val getEnabledSources = GetEnabledSources(repository, preferences)
            val enabled = getEnabledSources.subscribe()
                .first { list -> list.any { it.name == "Fake Source" } }
            enabled.any { it.name == "Fake Source" } shouldBe true

            val viewModel = SourcesViewModel(
                getEnabledSources,
                mockk(relaxed = true),
                mockk(relaxed = true),
            )
            val state = viewModel.state.first { it.items.isNotEmpty() }
            state.items.filterIsInstance<SourceUiModel.Item>()
                .any { it.source.name == "Fake Source" } shouldBe true
        } finally {
            Dispatchers.resetMain()
        }
    }
}
