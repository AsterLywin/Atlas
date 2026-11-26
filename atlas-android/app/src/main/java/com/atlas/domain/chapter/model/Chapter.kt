package com.atlas.domain.chapter.model

import atlas.core.common.extensions.toEpochMillisOrZero
import atlas.core.common.extensions.toInstantOrNull
import atlas.domain.chapter.model.Chapter
import com.atlas.reader.source.model.SChapter

fun Chapter.toSChapter(): SChapter {
    return SChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload.toEpochMillisOrZero()
        it.chapter_number = chapterNumber.toFloat()
        it.scanlator = scanlator
        it.memo = memo
    }
}

fun Chapter.copyFromSChapter(sChapter: SChapter): Chapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload.toInstantOrNull(),
        chapterNumber = sChapter.chapter_number.toDouble(),
        scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
        memo = sChapter.memo,
    )
}
