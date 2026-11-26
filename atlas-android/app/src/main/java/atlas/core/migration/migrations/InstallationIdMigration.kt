package atlas.core.migration.migrations

import atlas.core.common.FeatureFlags
import atlas.core.migration.Migration
import atlas.core.migration.MigrationContext
import com.atlas.domain.base.BasePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlin.uuid.ExperimentalUuidApi

@Inject
@ContributesIntoSet(AppScope::class)
class InstallationIdMigration(
    private val basePreferences: BasePreferences,
) : Migration {
    override val version: Float = Migration.ALWAYS

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val installationId = basePreferences.installationId
        if (!installationId.isSet()) installationId.set(FeatureFlags.newInstallationId())
        return true
    }
}
