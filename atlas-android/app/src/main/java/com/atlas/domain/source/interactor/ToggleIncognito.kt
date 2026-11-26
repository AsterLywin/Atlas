package com.atlas.domain.source.interactor

import atlas.core.common.preference.getAndSet
import com.atlas.domain.source.service.SourcePreferences
import dev.zacsweers.metro.Inject

@Inject
class ToggleIncognito(
    private val preferences: SourcePreferences,
) {
    fun await(extensions: String, enable: Boolean) {
        preferences.incognitoExtensions.getAndSet {
            if (enable) it.plus(extensions) else it.minus(extensions)
        }
    }
}
