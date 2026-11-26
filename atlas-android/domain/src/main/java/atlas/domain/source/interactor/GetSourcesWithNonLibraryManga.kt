package atlas.domain.source.interactor

import atlas.domain.source.model.SourceWithCount
import atlas.domain.source.repository.SourceRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@Inject
class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return repository.getSourcesWithNonLibraryManga()
    }
}
