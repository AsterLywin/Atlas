package com.atlas.reader.data.backup.create.creators

import atlas.domain.extension.interactor.GetExtensionStores
import com.atlas.reader.data.backup.models.BackupExtensionStore
import com.atlas.reader.data.backup.models.backupExtensionStoreMapper
import dev.zacsweers.metro.Inject

@Inject
class ExtensionStoresBackupCreator(
    private val getExtensionStores: GetExtensionStores,
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return getExtensionStores.get()
            .map(backupExtensionStoreMapper)
    }
}
