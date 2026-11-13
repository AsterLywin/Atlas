package com.atlas.domain.source.interactor

import dev.zacsweers.metro.Inject
import com.atlas.domain.source.service.SourcePreferences
import atlas.core.common.preference.getAndSet

@Inject
class ToggleLanguage(
    val preferences: SourcePreferences,
) {

    fun await(language: String) {
        val isEnabled = language in preferences.enabledLanguages.get()
        preferences.enabledLanguages.getAndSet { enabled ->
            if (isEnabled) enabled.minus(language) else enabled.plus(language)
        }
    }
}
