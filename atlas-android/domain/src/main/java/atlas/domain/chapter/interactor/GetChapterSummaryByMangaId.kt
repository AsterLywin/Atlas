package atlas.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import atlas.domain.chapter.model.ChapterSummary
import atlas.domain.chapter.repository.ChapterRepository

@Inject
class GetChapterSummaryByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): ChapterSummary {
        return chapterRepository.getChapterSummaryByMangaId(mangaId)
    }
}
