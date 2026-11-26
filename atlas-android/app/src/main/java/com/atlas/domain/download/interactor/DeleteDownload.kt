package com.atlas.domain.download.interactor

import atlas.domain.chapter.model.Chapter
import atlas.domain.manga.model.Manga
import atlas.domain.source.service.SourceManager
import com.atlas.reader.data.download.DownloadManager
import dev.zacsweers.metro.Inject

@Inject
class DeleteDownload(
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(chapters.toList(), manga, source)
        }
    }
}
