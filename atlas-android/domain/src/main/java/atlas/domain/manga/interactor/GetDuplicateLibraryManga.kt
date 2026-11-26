package atlas.domain.manga.interactor

import atlas.domain.manga.model.Manga
import atlas.domain.manga.model.MangaWithChapterCount
import atlas.domain.manga.repository.MangaRepository
import dev.zacsweers.metro.Inject

@Inject
class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
    }
}
