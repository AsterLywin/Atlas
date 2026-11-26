package atlas.domain.extension.interactor

import atlas.domain.extension.repository.ExtensionStoreRepository
import dev.zacsweers.metro.Inject

@Inject
class AddExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String): Result<Unit> {
        return repository.insert(indexUrl)
    }
}
