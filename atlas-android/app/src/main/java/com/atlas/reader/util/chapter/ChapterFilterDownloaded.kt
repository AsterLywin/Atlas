package com.atlas.reader.util.chapter

import com.atlas.reader.data.download.DownloadCache
import atlas.domain.chapter.model.Chapter
import atlas.domain.manga.model.Manga
import atlas.source.local.isLocal

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga, downloadCache: DownloadCache): List<Chapter> {
    if (manga.isLocal()) return this

    return filter { downloadCache.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source) }
}
