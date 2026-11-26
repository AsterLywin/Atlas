package atlas.domain.chapter.interactor

import atlas.domain.chapter.model.Chapter
import atlas.domain.chapter.repository.ChapterRepository
import dev.zacsweers.metro.Inject

@Inject
class GetChapterByUrlAndMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(url: String, sourceId: Long): Chapter? {
        return try {
            chapterRepository.getChapterByUrlAndMangaId(url, sourceId)
        } catch (e: Exception) {
            null
        }
    }
}
