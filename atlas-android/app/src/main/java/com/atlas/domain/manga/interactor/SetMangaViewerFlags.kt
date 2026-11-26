package com.atlas.domain.manga.interactor

import atlas.domain.manga.model.MangaUpdate
import atlas.domain.manga.repository.MangaRepository
import com.atlas.reader.ui.reader.setting.ReaderOrientation
import com.atlas.reader.ui.reader.setting.ReadingMode
import dev.zacsweers.metro.Inject

@Inject
class SetMangaViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitSetReadingMode(id: Long, flag: Long) {
        val manga = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(id) {
                viewerFlags = manga.viewerFlags.setFlag(flag, ReadingMode.MASK.toLong())
            },
        )
    }

    suspend fun awaitSetOrientation(id: Long, flag: Long) {
        val manga = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(id) {
                viewerFlags = manga.viewerFlags.setFlag(flag, ReaderOrientation.MASK.toLong())
            },
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
