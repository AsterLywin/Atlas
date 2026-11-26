package com.atlas.reader.util

import atlas.domain.manga.model.Manga
import atlas.source.local.image.LocalCoverManager
import atlas.source.local.isLocal
import com.atlas.domain.manga.interactor.UpdateManga
import com.atlas.domain.manga.model.toSManga
import com.atlas.reader.data.cache.CoverCache
import java.io.InputStream

fun Manga.removeCovers(coverCache: CoverCache) {
    if (isLocal()) return
    coverCache.deleteFromCache(this, true)
}

suspend fun Manga.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateManga: UpdateManga,
    coverCache: CoverCache,
) {
    if (isLocal()) {
        coverManager.update(toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}
