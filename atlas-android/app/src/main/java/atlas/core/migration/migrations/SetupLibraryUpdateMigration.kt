package atlas.core.migration.migrations

import android.content.Context
import atlas.core.migration.Migration
import atlas.core.migration.MigrationContext
import com.atlas.reader.data.library.LibraryUpdateWorker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

@Inject
@ContributesIntoSet(AppScope::class)
class SetupLibraryUpdateMigration(
    private val context: Context,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        LibraryUpdateWorker.setupTask(context)
        return true
    }
}
