package atlas.core.migration.migrations

import android.content.Context
import atlas.core.migration.Migration
import atlas.core.migration.MigrationContext
import com.atlas.reader.data.backup.create.BackupCreateWorker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class SetupBackupCreateMigration(
    private val context: Context,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        BackupCreateWorker.setupTask(context)
        return true
    }
}
