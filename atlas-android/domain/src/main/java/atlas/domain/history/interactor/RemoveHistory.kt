package atlas.domain.history.interactor

import atlas.domain.history.model.HistoryWithRelations
import atlas.domain.history.repository.HistoryRepository
import dev.zacsweers.metro.Inject

@Inject
class RemoveHistory(
    private val repository: HistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllHistory()
    }

    suspend fun await(history: HistoryWithRelations) {
        repository.resetHistory(history.id)
    }

    suspend fun await(mangaId: Long) {
        repository.resetHistoryByMangaId(mangaId)
    }
}
