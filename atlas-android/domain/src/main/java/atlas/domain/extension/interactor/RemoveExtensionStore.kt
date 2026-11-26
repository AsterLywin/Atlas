package atlas.domain.extension.interactor

import atlas.domain.extension.repository.ExtensionStoreRepository
import dev.zacsweers.metro.Inject

@Inject
class RemoveExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
