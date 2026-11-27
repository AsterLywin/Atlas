package com.atlas.reader.ui.shared

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.reader.source.kotatsu.AndroidMangaLoaderContext
import com.atlas.ui.AndroidSettingsStore
import com.atlas.ui.AtlasApp
import com.atlas.ui.InMemoryProgressStore
import com.atlas.ui.NoOpDownloadManager
import java.util.Locale

/**
 * The shared Compose UI running on Android: library, browse, book
 * detail, reader and settings from `:atlas-ui`, the same screens the
 * desktop app shows.
 *
 * This is a standalone activity on purpose — the app's own reader
 * and navigation (Voyager, Metro DI) are untouched; this activity
 * only proves the shared module is consumable from Android and that
 * at least one shared screen (Browse, backed by the real Kotatsu
 * sources) is reachable there.
 *
 * Reachable with an explicit intent (not exported, no launcher
 * entry):
 * `adb shell am start -n com.atlas.reader/.ui.shared.AtlasSharedUiActivity`
 * (the manifest package is `com.atlas.reader`).
 *
 * Platform notes: settings persist in SharedPreferences
 * ([AndroidSettingsStore]); reading progress is in-memory only
 * ([InMemoryProgressStore]) until the shared UI is wired to the
 * Android library store; downloads are a no-op stub
 * ([NoOpDownloadManager]) — Android downloads are not this wave's
 * work and the app's own downloader is untouched; the library here is
 * in-memory only (see [AtlasSharedRepository]). The loader context is
 * built directly, not from the Metro graph, so this activity needs no DI.
 */
class AtlasSharedUiActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val registry = KotatsuParserRegistry()
        val loaderContext = AndroidMangaLoaderContext()
        val repository = AtlasSharedRepository(registry, loaderContext)
        val imageLoaders = AtlasSharedImageLoaders(this, loaderContext, registry)
        setContent {
            AtlasApp(
                repository = repository,
                imageLoaderForBook = { book -> imageLoaders.forSource(book.sourceId) },
                progressStore = InMemoryProgressStore(),
                settingsStore = AndroidSettingsStore(applicationContext),
                systemLanguage = Locale.getDefault().language,
                downloadManager = NoOpDownloadManager(),
            )
        }
    }
}
