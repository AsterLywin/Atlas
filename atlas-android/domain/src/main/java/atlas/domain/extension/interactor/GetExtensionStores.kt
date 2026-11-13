package atlas.domain.extension.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import atlas.domain.extension.model.ExtensionStore
import atlas.domain.extension.repository.ExtensionStoreRepository

@Inject
class GetExtensionStores(
    private val repository: ExtensionStoreRepository,
) {
    suspend fun get(): List<ExtensionStore> = repository.getAll()

    fun subscribe(): Flow<List<ExtensionStore>> = repository.getAllAsFlow()
}
