package atlas.domain.backup.repository

import atlas.domain.backup.model.RestoredManga
import atlas.domain.manga.model.Manga
import atlas.domain.manga.model.MangaUpdate

interface RestoreRepository {

    /**
     * Restores [entries] all together: either every one of them is restored or none is. [update] is what to change on each
     * entry once its chapters and history are in place, and is part of the same all-or-nothing restore.
     */
    suspend fun restoreManga(entries: List<RestoredManga>, update: suspend (Manga) -> MangaUpdate)
}
