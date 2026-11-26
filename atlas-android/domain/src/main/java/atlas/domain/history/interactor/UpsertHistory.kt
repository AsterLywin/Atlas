package atlas.domain.history.interactor

import atlas.domain.history.model.HistoryUpdate
import atlas.domain.history.repository.HistoryRepository
import dev.zacsweers.metro.Inject

@Inject
class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        historyRepository.upsertHistory(historyUpdate)
    }
}
