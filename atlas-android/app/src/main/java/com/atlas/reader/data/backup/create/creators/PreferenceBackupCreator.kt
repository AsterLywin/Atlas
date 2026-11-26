package com.atlas.reader.data.backup.create.creators

import atlas.core.common.preference.Preference
import atlas.core.common.preference.PreferenceStore
import atlas.domain.source.service.SourceManager
import com.atlas.reader.data.backup.models.BackupPreference
import com.atlas.reader.data.backup.models.BackupSourcePreferences
import com.atlas.reader.data.backup.models.BooleanPreferenceValue
import com.atlas.reader.data.backup.models.FloatPreferenceValue
import com.atlas.reader.data.backup.models.IntPreferenceValue
import com.atlas.reader.data.backup.models.LongPreferenceValue
import com.atlas.reader.data.backup.models.StringPreferenceValue
import com.atlas.reader.data.backup.models.StringSetPreferenceValue
import com.atlas.reader.source.ConfigurableSource
import com.atlas.reader.source.preferenceKey
import com.atlas.reader.source.sourcePreferences
import dev.zacsweers.metro.Inject

@Inject
class PreferenceBackupCreator(
    private val sourceManager: SourceManager,
    private val preferenceStore: PreferenceStore,
) {

    fun createApp(includePrivatePreferences: Boolean): List<BackupPreference> {
        return preferenceStore.getAll().toBackupPreferences()
            .withPrivatePreferences(includePrivatePreferences)
    }

    suspend fun createSource(includePrivatePreferences: Boolean): List<BackupSourcePreferences> {
        return sourceManager.getAll()
            .filterIsInstance<ConfigurableSource>()
            .map {
                BackupSourcePreferences(
                    it.preferenceKey(),
                    it.sourcePreferences().all.toBackupPreferences()
                        .withPrivatePreferences(includePrivatePreferences),
                )
            }
            .filter { it.prefs.isNotEmpty() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, *>.toBackupPreferences(): List<BackupPreference> {
        return this
            .filterKeys { !Preference.isAppState(it) }
            .mapNotNull { (key, value) ->
                when (value) {
                    is Int -> BackupPreference(key, IntPreferenceValue(value))
                    is Long -> BackupPreference(key, LongPreferenceValue(value))
                    is Float -> BackupPreference(key, FloatPreferenceValue(value))
                    is String -> BackupPreference(key, StringPreferenceValue(value))
                    is Boolean -> BackupPreference(key, BooleanPreferenceValue(value))
                    is Set<*> -> (value as? Set<String>)?.let {
                        BackupPreference(key, StringSetPreferenceValue(it))
                    }
                    else -> null
                }
            }
    }

    private fun List<BackupPreference>.withPrivatePreferences(include: Boolean) =
        if (include) {
            this
        } else {
            this.filter { !Preference.isPrivate(it.key) }
        }
}
