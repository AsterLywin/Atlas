package com.atlas.ui.data

import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.DownloadManager

/**
 * Which page locations the reader opens for one chapter.
 *
 * A fully downloaded chapter reads from its local files — offline,
 * byte-identical to what the download stored — and only a chapter
 * that is not downloaded falls back to the source's page URLs.
 * An empty local list is treated as "not downloaded": a chapter dir
 * with a `.complete` marker and no pages is corrupt, and the URLs
 * are a better answer than an empty reader.
 */
suspend fun resolveReaderPages(
    book: UiBook,
    chapter: UiChapter,
    repository: AtlasRepository,
    downloadManager: DownloadManager,
): List<String> {
    val local = downloadManager.localPages(book, chapter)
    if (!local.isNullOrEmpty()) return local
    return repository.loadPages(chapter.id)
}
