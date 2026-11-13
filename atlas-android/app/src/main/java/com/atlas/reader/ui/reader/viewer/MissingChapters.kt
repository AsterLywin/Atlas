package com.atlas.reader.ui.reader.viewer

import com.atlas.reader.ui.reader.model.ReaderChapter
import atlas.domain.chapter.service.calculateChapterGap as domainCalculateChapterGap

fun calculateChapterGap(higherReaderChapter: ReaderChapter?, lowerReaderChapter: ReaderChapter?): Int {
    return domainCalculateChapterGap(
        higherReaderChapter?.chapter,
        lowerReaderChapter?.chapter,
    )
}
