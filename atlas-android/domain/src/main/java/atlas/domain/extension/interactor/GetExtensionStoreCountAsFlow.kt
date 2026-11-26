package atlas.domain.extension.interactor

import atlas.domain.extension.repository.ExtensionStoreRepository
import dev.zacsweers.metro.Inject

@Inject
class GetExtensionStoreCountAsFlow(
    private val repository: ExtensionStoreRepository,
) {
    operator fun invoke() = repository.getCountAsFlow()
}
