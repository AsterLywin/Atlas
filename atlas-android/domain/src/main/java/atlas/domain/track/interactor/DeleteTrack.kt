package atlas.domain.track.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import atlas.core.common.util.system.logcat
import atlas.domain.track.repository.TrackRepository

@Inject
class DeleteTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(mangaId: Long, trackerId: Long) {
        try {
            trackRepository.delete(mangaId, trackerId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
