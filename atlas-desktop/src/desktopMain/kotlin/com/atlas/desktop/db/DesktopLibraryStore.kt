package com.atlas.desktop.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import java.io.Closeable
import java.io.File

/**
 * The desktop library, backed by the SQLDelight database in [dbFile].
 * Same API shape as the core in-memory [com.atlas.core.library.LibraryStore],
 * but the books survive an app restart: every write goes to the file.
 *
 * REAL columns come back as Double from SQLDelight; they are mapped to
 * the core Float fields here (the conversion is exact — a chapter
 * number of 1.5 stored as REAL reads back as exactly 1.5f).
 */
class DesktopLibraryStore private constructor(
    private val driver: JdbcSqliteDriver,
    private val db: AtlasDatabase,
) : Closeable {
    private val queries get() = db.libraryQueries

    fun addBook(book: Book, bookChapters: List<Chapter> = emptyList()) {
        queries.transaction {
            queries.insertBook(
                id = book.id,
                title = book.title,
                author = book.author,
                source_id = book.sourceId,
                cover_url = book.coverUrl,
            )
            queries.deleteChapters(book.id)
            val sorted = bookChapters.sortedBy { it.number }
            for (chapter in sorted) {
                queries.insertChapter(
                    id = chapter.id,
                    book_id = book.id,
                    title = chapter.title,
                    number = chapter.number.toDouble(),
                    read = chapter.read,
                    progress = chapter.progress.toDouble(),
                )
            }
        }
    }

    fun removeBook(bookId: String) {
        queries.transaction {
            queries.deleteChapters(bookId)
            queries.deleteBook(bookId)
        }
    }

    fun getBook(bookId: String): Book? = queries.selectBook(bookId).executeAsOneOrNull()?.toBook()

    fun listBooks(): List<Book> = queries.selectBooks().executeAsList().map { it.toBook() }

    fun listChapters(bookId: String): List<Chapter> =
        queries.selectChapters(bookId).executeAsList().map { it.toChapter() }

    fun markChapterRead(bookId: String, chapterId: String, progress: Float = 1f) {
        val clamped = progress.coerceIn(0f, 1f)
        queries.transaction {
            queries.updateChapterRead(read = clamped >= 1f, progress = clamped.toDouble(), id = chapterId)
            val chapters = queries.selectChapters(bookId).executeAsList()
            val finished = chapters.isNotEmpty() && chapters.all { it.read }
            queries.updateBookProgress(
                last_read_chapter_id = chapterId,
                last_read_position = clamped.toDouble(),
                finished = finished,
                id = bookId,
            )
        }
    }

    override fun close() {
        driver.close()
    }

    companion object {
        /**
         * Opens (creating it and its parent dirs when needed) the database
         * at [dbFile]. The driver is built with the schema, so it creates
         * the tables on a fresh file and migrates an existing one by
         * `PRAGMA user_version` — reopening never runs CREATE TABLE twice.
         */
        fun open(dbFile: File): DesktopLibraryStore {
            dbFile.parentFile?.mkdirs()
            val driver = JdbcSqliteDriver(
                url = "jdbc:sqlite:${dbFile.absolutePath}",
                schema = AtlasDatabase.Schema,
            )
            return DesktopLibraryStore(driver, AtlasDatabase(driver))
        }
    }
}

private fun Library_book.toBook(): Book = Book(
    id = id,
    title = title,
    author = author,
    sourceId = source_id,
    coverUrl = cover_url,
    lastReadChapterId = last_read_chapter_id,
    lastReadPosition = last_read_position.toFloat(),
    finished = finished,
)

private fun Library_chapter.toChapter(): Chapter = Chapter(
    id = id,
    bookId = book_id,
    title = title,
    number = number.toFloat(),
    read = read,
    progress = progress.toFloat(),
)
