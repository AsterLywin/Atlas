package com.atlas.reader.data.backup.restore.restorers

import atlas.domain.extension.model.ExtensionStore
import atlas.domain.extension.repository.ExtensionStoreRepository
import com.atlas.reader.data.backup.models.BackupExtensionStore
import dev.zacsweers.metro.Inject

@Inject
class ExtensionStoreRestorer(
    private val extensionStoreRepository: ExtensionStoreRepository,
) {

    suspend operator fun invoke(
        backupStore: BackupExtensionStore,
    ) {
        extensionStoreRepository.upsert(
            ExtensionStore(
                indexUrl = backupStore.indexUrl,
                name = backupStore.name,
                badgeLabel = backupStore.badgeLabel ?: backupStore.name,
                signingKey = backupStore.signingKey,
                contact = ExtensionStore.Contact(
                    website = backupStore.contactWebsite,
                    discord = backupStore.contactDiscord,
                ),
                isLegacy = backupStore.isLegacy ?: true,
                extensionListUrl = backupStore.extensionListUrl,
            ),
        )
    }
}
