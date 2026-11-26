package com.atlas.reader.util.chapter

import atlas.domain.chapter.model.Chapter
import atlas.domain.manga.model.Manga
import atlas.source.local.isLocal
import com.atlas.reader.data.download.DownloadCache

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga, downloadCache: DownloadCache): List<Chapter> {
    if (manga.isLocal()) return this

    return filter { downloadCache.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source) }
}
