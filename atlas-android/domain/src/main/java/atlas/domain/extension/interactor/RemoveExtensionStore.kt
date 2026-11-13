package atlas.domain.extension.interactor

import dev.zacsweers.metro.Inject
import atlas.domain.extension.repository.ExtensionStoreRepository

@Inject
class RemoveExtensionStore(
    private val repository: ExtensionStoreRepository,
) {
    suspend operator fun invoke(indexUrl: String) {
        repository.remove(indexUrl)
    }
}
