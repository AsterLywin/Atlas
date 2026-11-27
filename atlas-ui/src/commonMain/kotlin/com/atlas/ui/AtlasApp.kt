package com.atlas.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.AtlasDestination
import com.atlas.ui.state.AtlasNavigator
import com.atlas.ui.state.BackupActions
import com.atlas.ui.state.BackupOutcome
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.state.ReadingProgressStore
import com.atlas.ui.state.SettingsStore
import com.atlas.ui.theme.AtlasTheme
import com.atlas.ui.theme.LocalAtlasStrings
import com.atlas.ui.theme.resolveStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The shared app shell: bottom-bar navigation between Library /
 * Browse / Settings, with book detail and the reader pushed on top.
 * The platform shell only supplies the [AtlasRepository], the
 * per-book Coil [ImageLoader], the progress + settings stores, the
 * download manager, the device language, and — when the platform
 * implements backup — the [BackupActions]; everything on screen is
 * common code.
 */
@Composable
fun AtlasApp(
    repository: AtlasRepository,
    imageLoaderForBook: (UiBook) -> ImageLoader,
    progressStore: ReadingProgressStore,
    settingsStore: SettingsStore,
    systemLanguage: String,
    downloadManager: DownloadManager,
    backupActions: BackupActions? = null,
    modifier: Modifier = Modifier,
) {
    var settings by remember { mutableStateOf(settingsStore.load()) }
    val strings = resolveStrings(settings.language, systemLanguage)
    val navigator = remember { AtlasNavigator() }
    var libraryVersion by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var backupStatus by remember { mutableStateOf<BackupOutcome?>(null) }

    // The book currently open in detail / reader, with its chapters.
    var openBook by remember { mutableStateOf<UiBook?>(null) }
    var openChapters by remember { mutableStateOf<List<UiChapter>?>(null) }
    var openChaptersError by remember { mutableStateOf(false) }
    var openInLibrary by remember { mutableStateOf(false) }
    var chaptersVersion by remember { mutableStateOf(0) }

    // A restore changes the library behind the screens' backs.
    LaunchedEffect(backupStatus) {
        if (backupStatus is BackupOutcome.Restored) {
            libraryVersion++
            chaptersVersion++
        }
    }

    fun openBookDetail(book: UiBook) {
        openBook = book
        openChapters = null
        openChaptersError = false
        chaptersVersion++
        navigator.navigateTo(AtlasDestination.BookDetail(book.id))
    }

    // Load the open book's chapters (library first, source otherwise).
    val openBookId = (navigator.current as? AtlasDestination.BookDetail)?.bookId
        ?: (navigator.current as? AtlasDestination.Reader)?.bookId
    LaunchedEffect(openBookId, chaptersVersion) {
        val book = openBook ?: return@LaunchedEffect
        openInLibrary = repository.getLibraryBook(book.id) != null
        openChaptersError = false
        openChapters = try {
            if (openInLibrary) {
                repository.listLibraryChapters(book.id)
            } else {
                repository.loadChapters(book)
            }
        } catch (_: Exception) {
            openChaptersError = true
            emptyList()
        }
    }

    AtlasTheme(theme = settings.theme, strings = strings) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (navigator.current.isRoot) {
                    AtlasBottomBar(
                        current = navigator.current,
                        onSelect = { navigator.navigateTo(it) },
                    )
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (val destination = navigator.current) {
                    AtlasDestination.Library -> LibraryScreen(
                        repository = repository,
                        imageLoaderForBook = imageLoaderForBook,
                        version = libraryVersion,
                        onOpenBook = ::openBookDetail,
                    )
                    AtlasDestination.Browse -> BrowseScreen(
                        repository = repository,
                        imageLoaderForBook = imageLoaderForBook,
                        onOpenBook = ::openBookDetail,
                    )
                    AtlasDestination.Settings -> SettingsScreen(
                        settings = settings,
                        onSettingsChange = { newSettings ->
                            settings = newSettings
                            scope.launch(Dispatchers.IO) { settingsStore.save(newSettings) }
                        },
                        backupStatus = backupStatus,
                        onCreateBackup = backupActions?.let {
                            {
                                scope.launch(Dispatchers.IO) {
                                    backupStatus = it.createBackup()
                                }
                            }
                        },
                        onRestoreBackup = backupActions?.let {
                            {
                                scope.launch(Dispatchers.IO) {
                                    backupStatus = it.restoreBackup()
                                }
                            }
                        },
                    )
                    is AtlasDestination.BookDetail -> {
                        val book = openBook
                        if (book == null) {
                            Text(strings.loading)
                        } else {
                            BookDetailScreen(
                                book = book,
                                inLibrary = openInLibrary,
                                chapters = openChapters,
                                loadError = openChaptersError,
                                downloadManager = downloadManager,
                                onReloadChapters = { chaptersVersion++ },
                                onAddToLibrary = { chapters ->
                                    scope.launch {
                                        repository.addToLibrary(book, chapters)
                                        libraryVersion++
                                        openInLibrary = true
                                        chaptersVersion++
                                    }
                                },
                                onRemoveFromLibrary = {
                                    scope.launch {
                                        repository.removeFromLibrary(book.id)
                                        libraryVersion++
                                        openInLibrary = false
                                        chaptersVersion++
                                    }
                                },
                                onOpenChapter = { chapter ->
                                    navigator.navigateTo(
                                        AtlasDestination.Reader(book.id, chapter.id),
                                    )
                                },
                                onBack = { navigator.navigateBack() },
                            )
                        }
                    }
                    is AtlasDestination.Reader -> {
                        val book = openBook
                        val chapter = openChapters?.firstOrNull { it.id == destination.chapterId }
                        if (book == null || chapter == null) {
                            Text(strings.loading)
                        } else {
                            ReaderScreen(
                                book = book,
                                chapter = chapter,
                                chapters = openChapters.orEmpty(),
                                defaultMode = settings.readerMode,
                                repository = repository,
                                imageLoaderForBook = imageLoaderForBook,
                                progressStore = progressStore,
                                downloadManager = downloadManager,
                                onBack = { navigator.navigateBack() },
                                onOpenChapter = { next ->
                                    navigator.replaceTop(
                                        AtlasDestination.Reader(book.id, next.id),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtlasBottomBar(
    current: AtlasDestination,
    onSelect: (AtlasDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            AtlasBottomBarItem(
                text = strings.tabLibrary,
                selected = current == AtlasDestination.Library,
                onClick = { onSelect(AtlasDestination.Library) },
            )
            AtlasBottomBarItem(
                text = strings.tabBrowse,
                selected = current == AtlasDestination.Browse,
                onClick = { onSelect(AtlasDestination.Browse) },
            )
            AtlasBottomBarItem(
                text = strings.tabSettings,
                selected = current == AtlasDestination.Settings,
                onClick = { onSelect(AtlasDestination.Settings) },
            )
        }
    }
}

@Composable
private fun RowScope.AtlasBottomBarItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
