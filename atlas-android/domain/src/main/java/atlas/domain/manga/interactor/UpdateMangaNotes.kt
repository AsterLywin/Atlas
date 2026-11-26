package atlas.domain.manga.interactor

import atlas.domain.manga.model.MangaUpdate
import atlas.domain.manga.repository.MangaRepository
import dev.zacsweers.metro.Inject

@Inject
class UpdateMangaNotes(
    private val mangaRepository: MangaRepository,
) {

    suspend operator fun invoke(mangaId: Long, notes: String): Boolean {
        return mangaRepository.update(
            MangaUpdate(mangaId) {
                this.notes = notes
            },
        )
    }
}
