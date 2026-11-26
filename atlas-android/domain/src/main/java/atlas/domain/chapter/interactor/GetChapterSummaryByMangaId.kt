package atlas.domain.chapter.interactor

import atlas.domain.chapter.model.ChapterSummary
import atlas.domain.chapter.repository.ChapterRepository
import dev.zacsweers.metro.Inject

@Inject
class GetChapterSummaryByMangaId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(mangaId: Long): ChapterSummary {
        return chapterRepository.getChapterSummaryByMangaId(mangaId)
    }
}
