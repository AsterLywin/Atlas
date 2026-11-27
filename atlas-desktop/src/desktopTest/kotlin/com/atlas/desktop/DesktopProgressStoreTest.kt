package com.atlas.desktop

import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.ui.state.ReadingProgress
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Reading progress on desktop survives an app restart: it is the
 * chapter's 0..1 progress in the library database.
 */
class DesktopProgressStoreTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-progress-test-${System.nanoTime()}")
            .also { it.mkdirs() }
        dbFile = tempDir.resolve("atlas.db")
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun seedBook(store: DesktopLibraryStore) {
        store.addBook(
            Book(
                id = "b1",
                title = "Fake Manga",
                sourceId = "kotatsu:MANGADEX",
            ),
            listOf(
                Chapter(id = "c1", bookId = "b1", title = "First", number = 1f),
                Chapter(id = "c2", bookId = "b1", title = "Second", number = 2f),
            ),
        )
    }

    @Test
    fun `progress saves and loads back as the same fraction`() {
        DesktopLibraryStore.open(dbFile).use { store ->
            seedBook(store)
            val progressStore = DesktopProgressStore(store)
            assertNull(progressStore.loadProgress("b1", "c1"))

            progressStore.saveProgress(ReadingProgress("b1", "c1", 0.5f))
            assertEquals(0.5f, progressStore.loadProgress("b1", "c1")?.fraction)

            progressStore.saveProgress(ReadingProgress("b1", "c1", 1f))
            assertEquals(1f, progressStore.loadProgress("b1", "c1")?.fraction)
        }
    }

    @Test
    fun `finishing the last chapter marks the book finished`() {
        DesktopLibraryStore.open(dbFile).use { store ->
            seedBook(store)
            val progressStore = DesktopProgressStore(store)
            progressStore.saveProgress(ReadingProgress("b1", "c1", 1f))
            progressStore.saveProgress(ReadingProgress("b1", "c2", 1f))
            assertTrue(store.getBook("b1")?.finished == true)
            assertTrue(store.listChapters("b1").all { it.read })
        }
    }

    @Test
    fun `progress survives close and reopen`() {
        DesktopLibraryStore.open(dbFile).use { store ->
            seedBook(store)
            DesktopProgressStore(store)
                .saveProgress(ReadingProgress("b1", "c2", 0.25f))
        }
        DesktopLibraryStore.open(dbFile).use { store ->
            assertEquals(0.25f, DesktopProgressStore(store).loadProgress("b1", "c2")?.fraction)
        }
    }

    @Test
    fun `unknown chapters load as no progress`() {
        DesktopLibraryStore.open(dbFile).use { store ->
            seedBook(store)
            assertNull(DesktopProgressStore(store).loadProgress("b1", "nope"))
            assertNull(DesktopProgressStore(store).loadProgress("nope", "c1"))
        }
    }
}
