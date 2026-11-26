package com.atlas.domain.source.interactor

import atlas.domain.source.model.Source
import atlas.domain.source.repository.SourceRepository
import com.atlas.domain.source.service.SourcePreferences
import com.atlas.reader.util.system.LocaleHelper
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.SortedMap

@Inject
class GetLanguagesWithSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<SortedMap<String, List<Source>>> {
        return combine(
            preferences.enabledLanguages.changes(),
            preferences.disabledSources.changes(),
            repository.getOnlineSources(),
        ) { enabledLanguage, disabledSource, onlineSources ->
            val sortedSources = onlineSources.sortedWith(
                compareBy<Source> { it.id.toString() in disabledSource }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

            sortedSources
                .groupBy { it.lang }
                .toSortedMap(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }
}
