package atlas.domain.category.interactor

import atlas.core.common.util.system.logcat
import atlas.domain.manga.repository.MangaRepository
import dev.zacsweers.metro.Inject
import logcat.LogPriority

@Inject
class SetMangaCategories(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(mangaId: Long, categoryIds: List<Long>) {
        try {
            mangaRepository.setMangaCategories(mangaId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
