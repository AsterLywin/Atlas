package atlas.domain.history.interactor

import dev.zacsweers.metro.Inject
import atlas.domain.history.model.HistoryUpdate
import atlas.domain.history.repository.HistoryRepository

@Inject
class UpsertHistory(
    private val historyRepository: HistoryRepository,
) {

    suspend fun await(historyUpdate: HistoryUpdate) {
        historyRepository.upsertHistory(historyUpdate)
    }
}
