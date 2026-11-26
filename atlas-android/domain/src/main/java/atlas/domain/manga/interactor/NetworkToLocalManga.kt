package atlas.domain.manga.interactor

import atlas.domain.manga.model.Manga
import atlas.domain.manga.repository.MangaRepository
import dev.zacsweers.metro.Inject

@Inject
class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(manga: Manga): Manga {
        return invoke(listOf(manga)).single()
    }

    suspend operator fun invoke(manga: List<Manga>): List<Manga> {
        return mangaRepository.insertNetworkManga(manga)
    }
}
