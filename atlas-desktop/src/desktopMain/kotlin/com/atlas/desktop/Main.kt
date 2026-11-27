package com.atlas.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.atlas.desktop.backup.DesktopBackupCreator
import com.atlas.desktop.backup.DesktopBackupRestorer
import com.atlas.desktop.backup.backupFileName
import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.desktop.download.DesktopDownloadManager
import com.atlas.parsers.AtlasMangaLoaderContext
import com.atlas.parsers.KotatsuParserRegistry
import com.atlas.ui.AtlasApp
import com.atlas.ui.FileSettingsStore
import com.atlas.ui.state.BackupActions
import com.atlas.ui.state.BackupOutcome
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atlas desktop entry point (Windows / Linux / macOS).
 *
 * A thin shell now: the whole UI — library, browse, book detail,
 * reader, settings — is the shared [AtlasApp] from `:atlas-ui`.
 * This file only wires the desktop implementations in: the Kotatsu
 * registry, the desktop SQLDelight store, the per-source Coil
 * loaders, the progress store on top of the library database, the
 * chapter download manager, the `.tachibk` backup actions, and the
 * settings file under the Atlas data dir.
 */
fun main() = application {
    val store = remember { DesktopLibraryStore.open(atlasDbFile()) }
    val loaderContext = remember { AtlasMangaLoaderContext() }
    val registry = remember { KotatsuParserRegistry() }
    val imageLoaders = remember { DesktopImageLoaders(loaderContext, registry, atlasImageCacheDir()) }
    val repository = remember { DesktopAtlasRepository(registry, loaderContext, store) }
    val progressStore = remember { DesktopProgressStore(store) }
    val downloadManager = remember {
        DesktopDownloadManager(loaderContext, registry, repository, atlasDownloadsDir())
    }
    val backupActions = remember {
        object : BackupActions {
            override suspend fun createBackup(): BackupOutcome =
                withContext(Dispatchers.IO) {
                    val dest = chooseFile("Create backup", FileDialog.SAVE, backupFileName())
                        ?: return@withContext BackupOutcome.Cancelled
                    runCatching {
                        val result = DesktopBackupCreator(store, registry).createBackup(dest)
                        BackupOutcome.Created(result.books, dest.name)
                    }.getOrElse { BackupOutcome.Failed(it.message ?: it.javaClass.simpleName) }
                }

            override suspend fun restoreBackup(): BackupOutcome =
                withContext(Dispatchers.IO) {
                    val chosen = chooseFile("Restore backup", FileDialog.LOAD, "*.tachibk")
                        ?: return@withContext BackupOutcome.Cancelled
                    runCatching {
                        val report = DesktopBackupRestorer(store, registry).restore(chosen)
                        BackupOutcome.Restored(report.books, report.chapters, report.skipped.size)
                    }.getOrElse { BackupOutcome.Failed(it.message ?: it.javaClass.simpleName) }
                }
        }
    }
    val settingsStore = remember {
        FileSettingsStore(atlasDataDir().resolve("atlas-settings.properties"))
    }
    Window(
        onCloseRequest = {
            downloadManager.close()
            store.close()
            exitApplication()
        },
        title = "Atlas",
    ) {
        AtlasApp(
            repository = repository,
            imageLoaderForBook = { book -> imageLoaders.forSource(book.sourceId) },
            progressStore = progressStore,
            settingsStore = settingsStore,
            systemLanguage = Locale.getDefault().language,
            downloadManager = downloadManager,
            backupActions = backupActions,
        )
    }
}

/** A native file picker; null when the user closes it without choosing. */
private fun chooseFile(title: String, mode: Int, fileName: String?): File? {
    val dialog = FileDialog(null as Frame?, title, mode)
    if (fileName != null) dialog.file = fileName
    dialog.isVisible = true
    val chosen = dialog.file ?: return null
    val dir = dialog.directory ?: return File(chosen)
    return File(dir, chosen)
}
