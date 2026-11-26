package atlas.domain.track.interactor

import atlas.core.common.util.system.logcat
import atlas.domain.track.model.Track
import atlas.domain.track.repository.TrackRepository
import dev.zacsweers.metro.Inject
import logcat.LogPriority

@Inject
class UpsertTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(track: Track) {
        try {
            trackRepository.upsert(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(tracks: List<Track>) {
        try {
            trackRepository.upsertAll(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
