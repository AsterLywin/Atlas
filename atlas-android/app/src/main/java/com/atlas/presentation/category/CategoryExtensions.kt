package com.atlas.presentation.category

import android.content.Context
import androidx.compose.runtime.Composable
import atlas.core.common.i18n.stringResource
import atlas.domain.category.model.Category
import atlas.i18n.MR
import atlas.presentation.core.i18n.stringResource

val Category.visualName: String
    @Composable
    get() = when {
        isSystemCategory -> stringResource(MR.strings.label_default)
        else -> name
    }

fun Category.visualName(context: Context): String =
    when {
        isSystemCategory -> context.stringResource(MR.strings.label_default)
        else -> name
    }
