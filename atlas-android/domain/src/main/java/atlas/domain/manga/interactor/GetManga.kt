package atlas.domain.manga.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import atlas.core.common.util.system.logcat
import atlas.domain.manga.model.Manga
import atlas.domain.manga.repository.MangaRepository

@Inject
class GetManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(id: Long): Manga? {
        return try {
            mangaRepository.getMangaById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    fun subscribe(id: Long): Flow<Manga> {
        return mangaRepository.getMangaByIdAsFlow(id)
    }

    fun subscribe(url: String, sourceId: Long): Flow<Manga?> {
        return mangaRepository.getMangaByUrlAndSourceIdAsFlow(url, sourceId)
    }
}
