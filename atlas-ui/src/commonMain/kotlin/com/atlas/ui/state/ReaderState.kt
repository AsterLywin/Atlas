package com.atlas.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlas.ui.model.ReaderMode
import kotlin.math.roundToInt

/**
 * The reader's state machine: which page is showing, in which mode.
 * Pure Kotlin (Compose state only so the UI recomposes) — no I/O, no
 * platform code — so the JVM tests drive it exactly like the UI does:
 * page turns, mode switches, progress save/restore.
 *
 * [progressFraction] is (index + 1) / pageCount: the last page is
 * 1.0, "finished". A single-page chapter is 1.0 as soon as it opens.
 */
class ReaderState(
    val pages: List<String>,
    initialIndex: Int = 0,
    initialMode: ReaderMode = ReaderMode.Pager,
) {
    val pageCount: Int get() = pages.size

    var currentIndex by mutableStateOf(initialIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
        private set

    var mode by mutableStateOf(initialMode)
        private set

    val progressFraction: Float
        get() = if (pageCount <= 0) {
            0f
        } else if (pageCount == 1) {
            1f
        } else {
            (currentIndex + 1).toFloat() / pageCount
        }

    /** True when [currentIndex] moved. False at the last page. */
    fun nextPage(): Boolean {
        if (currentIndex >= pageCount - 1) return false
        currentIndex++
        return true
    }

    /** True when [currentIndex] moved. False at the first page. */
    fun previousPage(): Boolean {
        if (currentIndex <= 0) return false
        currentIndex--
        return true
    }

    /** True when [currentIndex] moved. Out-of-range indexes are ignored. */
    fun goToPage(index: Int): Boolean {
        if (index !in 0 until pageCount || index == currentIndex) return false
        currentIndex = index
        return true
    }

    fun toggleMode() {
        mode = when (mode) {
            ReaderMode.Pager -> ReaderMode.Webtoon
            ReaderMode.Webtoon -> ReaderMode.Pager
        }
    }

    fun setReaderMode(newMode: ReaderMode) {
        mode = newMode
    }

    fun snapshot(bookId: String, chapterId: String): ReadingProgress =
        ReadingProgress(bookId, chapterId, progressFraction)

    companion object {
        /**
         * The page index a saved [fraction] means for [pageCount] pages:
         * the inverse of [progressFraction]. Exact for every index
         * (fraction is (index + 1) / count, so the round-trip is exact
         * up to Float rounding, which [roundToInt] absorbs).
         */
        fun restoredIndex(fraction: Float, pageCount: Int): Int {
            if (pageCount <= 0) return 0
            if (pageCount == 1) return 0
            return ((fraction.coerceIn(0f, 1f) * pageCount) - 1)
                .roundToInt()
                .coerceIn(0, pageCount - 1)
        }

        /** A state restored from a saved [progress], or from page 0. */
        fun restored(
            pages: List<String>,
            progress: ReadingProgress?,
            initialMode: ReaderMode,
        ): ReaderState = ReaderState(
            pages = pages,
            initialIndex = restoredIndex(progress?.fraction ?: 0f, pages.size),
            initialMode = initialMode,
        )
    }
}
