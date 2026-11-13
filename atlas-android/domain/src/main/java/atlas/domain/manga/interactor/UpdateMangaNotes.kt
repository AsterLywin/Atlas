package atlas.domain.manga.interactor

import dev.zacsweers.metro.Inject
import atlas.domain.manga.model.MangaUpdate
import atlas.domain.manga.repository.MangaRepository

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
