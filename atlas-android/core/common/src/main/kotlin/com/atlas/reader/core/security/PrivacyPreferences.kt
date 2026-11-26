package com.atlas.reader.core.security

import atlas.core.common.preference.Preference
import atlas.core.common.preference.PreferenceStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
class PrivacyPreferences(
    preferenceStore: PreferenceStore,
) {
    val crashlytics: Preference<Boolean> = preferenceStore.getBoolean("crashlytics", true)

    val analytics: Preference<Boolean> = preferenceStore.getBoolean("analytics", true)
}
