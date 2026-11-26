package com.atlas.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import atlas.app.di.appGraph
import atlas.i18n.MR
import atlas.presentation.core.i18n.stringResource
import com.atlas.domain.ui.UiPreferences
import com.atlas.reader.util.lang.toRelativeString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun relativeDateText(
    date: Instant?,
): String {
    return relativeDateText(
        localDate = date?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
    )
}

@Composable
fun relativeDateText(
    localDate: LocalDate?,
): String {
    val context = LocalContext.current
    val locale = LocalLocale.current

    val preferences = remember { context.appGraph.uiPreferences }
    val relativeTime = remember { preferences.relativeTime.get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat.get(), locale.platformLocale) }

    return localDate?.toRelativeString(
        context = context,
        relative = relativeTime,
        dateFormat = dateFormat,
    )
        ?: stringResource(MR.strings.not_applicable)
}
