package atlas.domain.chapter.interactor

import atlas.core.common.util.system.logcat
import atlas.domain.chapter.model.Chapter
import atlas.domain.chapter.repository.ChapterRepository
import dev.zacsweers.metro.Inject
import logcat.LogPriority

@Inject
class GetBookmarkedChaptersByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): List<Chapter> {
        return try {
            chapterRepository.getBookmarkedChaptersByMangaId(mangaId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
