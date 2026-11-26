package com.atlas.reader.data.backup.create.creators

import atlas.domain.source.service.SourceManager
import com.atlas.reader.data.backup.models.BackupSource
import com.atlas.reader.source.Source
import dev.zacsweers.metro.Inject

@Inject
class SourcesBackupCreator(
    private val sourceManager: SourceManager,
) {

    suspend operator fun invoke(sourceIds: Set<Long>): List<BackupSource> {
        return sourceIds.map { sourceManager.getOrStub(it).toBackupSource() }
    }
}

private fun Source.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
