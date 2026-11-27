package com.atlas.ui

import com.atlas.ui.model.ReaderMode
import com.atlas.ui.state.ReaderState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The reader state machine: page turns, modes, progress save/restore. */
class ReaderStateTest {

    private val pages = List(10) { "https://example.com/p$it.jpg" }

    @Test
    fun `starts at the first page with zero progress fraction`() {
        val state = ReaderState(pages)
        assertEquals(0, state.currentIndex)
        assertEquals(ReaderMode.Pager, state.mode)
        assertEquals(0.1f, state.progressFraction, 0.0001f)
    }

    @Test
    fun `next and previous page clamp at the ends`() {
        val state = ReaderState(pages)
        assertFalse(state.previousPage())
        assertEquals(0, state.currentIndex)

        repeat(9) { assertTrue(state.nextPage()) }
        assertEquals(9, state.currentIndex)
        assertEquals(1f, state.progressFraction, 0.0001f)
        assertFalse(state.nextPage())
        assertEquals(9, state.currentIndex)
    }

    @Test
    fun `goToPage moves and ignores out of range indexes`() {
        val state = ReaderState(pages)
        assertTrue(state.goToPage(4))
        assertEquals(4, state.currentIndex)
        assertEquals(0.5f, state.progressFraction, 0.0001f)
        assertFalse(state.goToPage(4))
        assertFalse(state.goToPage(-1))
        assertFalse(state.goToPage(10))
        assertEquals(4, state.currentIndex)
    }

    @Test
    fun `mode toggles between pager and webtoon and keeps the page`() {
        val state = ReaderState(pages, initialIndex = 3, initialMode = ReaderMode.Pager)
        state.toggleMode()
        assertEquals(ReaderMode.Webtoon, state.mode)
        assertEquals(3, state.currentIndex)
        state.setReaderMode(ReaderMode.Pager)
        assertEquals(ReaderMode.Pager, state.mode)
    }

    @Test
    fun `snapshot and restore round-trip every page`() {
        for (index in pages.indices) {
            val state = ReaderState(pages, initialIndex = index)
            val snapshot = state.snapshot("book", "chapter")
            val restored = ReaderState.restored(pages, snapshot, ReaderMode.Pager)
            assertEquals(index, restored.currentIndex, "page $index")
        }
    }

    @Test
    fun `restoredIndex is exact for many page counts`() {
        for (count in listOf(1, 2, 3, 5, 7, 10, 20, 100)) {
            val list = List(count) { "p$it" }
            for (index in list.indices) {
                val fraction = ReaderState(list, initialIndex = index).progressFraction
                assertEquals(index, ReaderState.restoredIndex(fraction, count), "count=$count index=$index")
            }
        }
    }

    @Test
    fun `no progress restores to the first page`() {
        val restored = ReaderState.restored(pages, null, ReaderMode.Webtoon)
        assertEquals(0, restored.currentIndex)
        assertEquals(ReaderMode.Webtoon, restored.mode)
    }

    @Test
    fun `a single page chapter is finished as soon as it opens`() {
        val state = ReaderState(listOf("only"))
        assertEquals(1f, state.progressFraction, 0.0001f)
        assertEquals(0, ReaderState.restoredIndex(1f, 1))
    }

    @Test
    fun `an empty page list has zero progress and no movement`() {
        val state = ReaderState(emptyList())
        assertEquals(0f, state.progressFraction, 0.0001f)
        assertFalse(state.nextPage())
        assertFalse(state.goToPage(0))
    }
}
