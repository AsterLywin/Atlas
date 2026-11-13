package atlas.domain.source.interactor

import dev.zacsweers.metro.Inject
import com.atlas.reader.source.model.FilterList
import atlas.domain.source.repository.SourcePagingSource
import atlas.domain.source.repository.SourceRepository

@Inject
class GetRemoteManga(
    private val repository: SourceRepository,
) {

    operator fun invoke(sourceId: Long, query: String, filterList: FilterList): SourcePagingSource {
        return when (query) {
            QUERY_POPULAR -> repository.getPopular(sourceId)
            QUERY_LATEST -> repository.getLatest(sourceId)
            else -> repository.search(sourceId, query, filterList)
        }
    }

    companion object {
        const val QUERY_POPULAR = "com.atlas.domain.source.interactor.POPULAR"
        const val QUERY_LATEST = "com.atlas.domain.source.interactor.LATEST"
    }
}
