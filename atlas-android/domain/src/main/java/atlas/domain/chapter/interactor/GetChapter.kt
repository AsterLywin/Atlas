package atlas.domain.chapter.interactor

import atlas.core.common.util.system.logcat
import atlas.domain.chapter.model.Chapter
import atlas.domain.chapter.repository.ChapterRepository
import dev.zacsweers.metro.Inject
import logcat.LogPriority

@Inject
class GetChapter(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(id: Long): Chapter? {
        return try {
            chapterRepository.getChapterById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun await(url: String, mangaId: Long): Chapter? {
        return try {
            chapterRepository.getChapterByUrlAndMangaId(url, mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }
}
