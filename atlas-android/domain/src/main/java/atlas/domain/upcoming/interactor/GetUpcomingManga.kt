package atlas.domain.upcoming.interactor

import atlas.domain.manga.model.Manga
import atlas.domain.manga.repository.MangaRepository
import com.atlas.reader.source.model.SManga
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@Inject
class GetUpcomingManga(
    private val mangaRepository: MangaRepository,
) {

    private val includedStatuses = setOf(
        SManga.ONGOING.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
    )

    suspend fun subscribe(
        excludedCategories: List<Long>,
        includedCategories: List<Long>,
    ): Flow<List<Manga>> {
        return mangaRepository.getUpcomingManga(
            includedStatuses,
            excludedCategories = excludedCategories,
            includedCategories = includedCategories,
        )
    }
}
