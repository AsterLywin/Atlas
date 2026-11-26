package atlas.domain.manga.interactor

import atlas.domain.manga.repository.MangaRepository
import dev.zacsweers.metro.Inject

@Inject
class ResetViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): Boolean {
        return mangaRepository.resetViewerFlags()
    }
}
