package com.atlas.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.data.resolveReaderPages
import com.atlas.ui.model.ReaderMode
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.state.ReaderState
import com.atlas.ui.state.ReadingProgress
import com.atlas.ui.state.ReadingProgressStore
import com.atlas.ui.theme.LocalAtlasStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The reader: pager mode (swipe page by page) or webtoon mode
 * (vertical scroll), pinch zoom + pan + double-tap zoom on every
 * page, and progress saved on every page turn.
 *
 * Pages come from [AtlasRepository.loadPages]; images load with the
 * parser host's own Coil [ImageLoader], so Referer-gated sources
 * work exactly like covers do.
 */
@Composable
fun ReaderScreen(
    book: UiBook,
    chapter: UiChapter,
    chapters: List<UiChapter>,
    defaultMode: ReaderMode,
    repository: AtlasRepository,
    imageLoaderForBook: (UiBook) -> ImageLoader,
    progressStore: ReadingProgressStore,
    downloadManager: DownloadManager,
    onBack: () -> Unit,
    onOpenChapter: (UiChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    var pages by remember(chapter.id) { mutableStateOf<List<String>?>(null) }
    var savedProgress by remember(chapter.id) { mutableStateOf<ReadingProgress?>(null) }
    var loadError by remember(chapter.id) { mutableStateOf(false) }
    var retryVersion by remember(chapter.id) { mutableStateOf(0) }

    LaunchedEffect(chapter.id, retryVersion) {
        loadError = false
        try {
            savedProgress = withContext(Dispatchers.IO) {
                progressStore.loadProgress(book.id, chapter.id)
            }
            pages = resolveReaderPages(book, chapter, repository, downloadManager)
        } catch (_: Exception) {
            loadError = true
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text(strings.back) }
            Spacer(Modifier.width(8.dp))
            Text(
                text = chapter.title.ifBlank {
                    strings.chapterTitle(formatChapterNumber(chapter.number))
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            ChapterDownloadControl(
                book = book,
                chapter = chapter,
                downloadManager = downloadManager,
            )
        }
        when {
            loadError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.loadError)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            pages = null
                            loadError = false
                            retryVersion++
                        },
                    ) { Text(strings.retry) }
                }
            }
            pages == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            pages!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.browseEmpty)
            }
            else -> ReaderContent(
                book = book,
                chapter = chapter,
                chapters = chapters,
                pages = pages!!,
                savedProgress = savedProgress,
                defaultMode = defaultMode,
                imageLoader = imageLoaderForBook(book),
                progressStore = progressStore,
                onOpenChapter = onOpenChapter,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReaderContent(
    book: UiBook,
    chapter: UiChapter,
    chapters: List<UiChapter>,
    pages: List<String>,
    savedProgress: ReadingProgress?,
    defaultMode: ReaderMode,
    imageLoader: ImageLoader,
    progressStore: ReadingProgressStore,
    onOpenChapter: (UiChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    val readerState = remember(pages, chapter.id) {
        ReaderState.restored(pages, savedProgress, defaultMode)
    }
    var zoomScale by remember { mutableStateOf(1f) }

    // Save progress on every page turn (and once on open).
    LaunchedEffect(readerState) {
        snapshotFlow { readerState.currentIndex }.collect {
            withContext(Dispatchers.IO) {
                progressStore.saveProgress(readerState.snapshot(book.id, chapter.id))
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = readerState.currentIndex,
    ) { readerState.pageCount }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = readerState.currentIndex,
    )

    // Pager swipes drive the state machine.
    LaunchedEffect(pagerState, readerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != readerState.currentIndex) readerState.goToPage(page)
        }
    }
    // Webtoon scrolls drive the state machine.
    LaunchedEffect(listState, readerState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index != readerState.currentIndex) readerState.goToPage(index)
        }
    }
    // A mode switch keeps the same page in view.
    LaunchedEffect(readerState.mode) {
        if (readerState.mode == ReaderMode.Webtoon) {
            listState.scrollToItem(readerState.currentIndex)
        } else {
            pagerState.scrollToPage(readerState.currentIndex)
        }
    }

    val chapterIndex = chapters.indexOfFirst { it.id == chapter.id }
    val previousChapter = chapters.getOrNull(chapterIndex - 1)
    val nextChapter = chapters.getOrNull(chapterIndex + 1)

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                strings.pageIndicator(readerState.currentIndex + 1, readerState.pageCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { readerState.toggleMode() }) {
                Text(
                    if (readerState.mode == ReaderMode.Pager) {
                        strings.readerModeWebtoon
                    } else {
                        strings.readerModePager
                    },
                )
            }
        }
        LinearProgressIndicator(
            progress = { readerState.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.weight(1f)) {
            when (readerState.mode) {
                ReaderMode.Pager -> HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = zoomScale <= 1.01f,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ZoomablePageImage(
                        model = pages[page],
                        imageLoader = imageLoader,
                        contentDescription = strings.pageIndicator(page + 1, pages.size),
                        onScaleChange = { zoomScale = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ReaderMode.Webtoon -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(pages, key = { it }) { url ->
                        ZoomablePageImage(
                            model = url,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            onScaleChange = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { previousChapter?.let(onOpenChapter) },
                enabled = previousChapter != null,
            ) { Text(strings.previousChapter) }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { nextChapter?.let(onOpenChapter) },
                enabled = nextChapter != null,
            ) { Text(strings.nextChapter) }
        }
    }
}

/**
 * One reader page with hand-rolled zoom: pinch to scale (1x..5x),
 * drag to pan while zoomed, double-tap to toggle 1x/2x. No zoom
 * library exists in the catalog, so the gestures are written with
 * [detectTransformGestures] + [graphicsLayer] directly.
 */
@Composable
private fun ZoomablePageImage(
    model: String,
    imageLoader: ImageLoader,
    contentDescription: String?,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) { onScaleChange(scale) }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            .pointerInput(model) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
                    },
                )
            }
            .pointerInput(model) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale <= 1f) Offset.Zero else offset + pan
                }
            },
    )
}
