package atlas.domain.source.models

import atlas.domain.chapter.model.Chapter
import atlas.domain.manga.model.Manga

data class RemoteMangaUpdate(
    val manga: Manga,
    val newChapters: List<Chapter>,
)
