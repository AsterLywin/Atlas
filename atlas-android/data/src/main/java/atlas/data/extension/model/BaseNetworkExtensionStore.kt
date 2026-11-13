package atlas.data.extension.model

import atlas.domain.extension.model.ExtensionStore

interface BaseNetworkExtensionStore {
    fun toExtensionStore(indexUrl: String): ExtensionStore
}
