package com.atlas.desktop.db

import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The desktop library survives an app restart: write books, close the
 * database file, open the same file again, read them back.
 */
class DesktopLibraryStoreTest {

    private lateinit var tempDir: File
    private lateinit var dbFile: File

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-desktop-test-${System.nanoTime()}")
            .also { it.mkdirs() }
        dbFile = tempDir.resolve("atlas.db")
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `books and fractional chapter numbers survive close and reopen`() {
        val book = Book(
            id = "b1",
            title = "Fake Manga",
            author = "Fake Author",
            sourceId = "kotatsu:MANGADEX",
            coverUrl = "https://example.com/cover.jpg",
        )
        val chapters = listOf(
            Chapter(id = "c1", bookId = "b1", title = "First", number = 1f),
            Chapter(id = "c1.5", bookId = "b1", title = "Half", number = 1.5f),
            Chapter(id = "c2", bookId = "b1", title = "Second", number = 2f),
        )
        DesktopLibraryStore.open(dbFile).use { store ->
            store.addBook(book, chapters)
            assertEquals(listOf(book), store.listBooks())
        }

        DesktopLibraryStore.open(dbFile).use { store ->
            val books = store.listBooks()
            assertEquals(1, books.size)
            assertEquals("Fake Manga", books[0].title)
            assertEquals("Fake Author", books[0].author)
            assertEquals("kotatsu:MANGADEX", books[0].sourceId)
            assertEquals("https://example.com/cover.jpg", books[0].coverUrl)
            // 1.5 is stored as REAL and must read back as exactly 1.5f.
            val readBack = store.listChapters("b1")
            assertEquals(listOf(1f, 1.5f, 2f), readBack.map { it.number })
            assertEquals("Half", readBack[1].title)
            assertEquals("b1", readBack[1].bookId)
        }
    }

    @Test
    fun `markChapterRead updates progress and finished, removeBook deletes everything`() {
        val book = Book(id = "b1", title = "Fake Manga", sourceId = "kotatsu:MANGADEX")
        val chapters = listOf(
            Chapter(id = "c1", bookId = "b1", title = "First", number = 1f),
            Chapter(id = "c2", bookId = "b1", title = "Second", number = 2f),
        )
        DesktopLibraryStore.open(dbFile).use { store ->
            store.addBook(book, chapters)
            store.markChapterRead("b1", "c1", progress = 1f)
            assertEquals(1f, store.getBook("b1")?.lastReadPosition)
            assertEquals("c1", store.getBook("b1")?.lastReadChapterId)
            assertEquals(false, store.getBook("b1")?.finished)
            assertEquals(true, store.listChapters("b1")[0].read)

            store.markChapterRead("b1", "c2", progress = 1f)
            assertEquals(true, store.getBook("b1")?.finished)

            store.removeBook("b1")
            assertNull(store.getBook("b1"))
            assertEquals(emptyList<Chapter>(), store.listChapters("b1"))
        }

        // The removal survives a reopen too.
        DesktopLibraryStore.open(dbFile).use { store ->
            assertEquals(emptyList<Book>(), store.listBooks())
        }
    }

    @Test
    fun `re-adding a book replaces its chapters`() {
        val book = Book(id = "b1", title = "Fake Manga", sourceId = "kotatsu:MANGADEX")
        DesktopLibraryStore.open(dbFile).use { store ->
            store.addBook(
                book,
                listOf(Chapter(id = "c1", bookId = "b1", title = "Old", number = 1f)),
            )
            store.addBook(
                book,
                listOf(
                    Chapter(id = "c1", bookId = "b1", title = "New", number = 1f),
                    Chapter(id = "c2", bookId = "b1", title = "Second", number = 2f),
                ),
            )
            val chapters = store.listChapters("b1")
            assertEquals(listOf("New", "Second"), chapters.map { it.title })
        }
    }
}
