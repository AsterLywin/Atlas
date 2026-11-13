package com.atlas.core.library

/**
 * A book in the library. `id` is the stable identity of the book (for local
 * files: the content hash; for catalogue books: the source id + remote id).
 * Progress is per book: the last-read chapter plus the position inside it.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String? = null,
    val sourceId: String,
    val coverUrl: String? = null,
    val lastReadChapterId: String? = null,
    /** 0..1 fraction read inside the last-read chapter. */
    val lastReadPosition: Float = 0f,
    val finished: Boolean = false,
)

/** One chapter (a "بخش") of a book, in reading order. */
data class Chapter(
    val id: String,
    val bookId: String,
    val title: String,
    /** 1-based reading order. */
    val number: Int,
    val read: Boolean = false,
    /** 0..1 fraction read inside this chapter. */
    val progress: Float = 0f,
)

/** In-memory library store. Persistence (database, backup) is phase 2. */
class LibraryStore {
    private val books = LinkedHashMap<String, Book>()
    private val chapters = HashMap<String, MutableList<Chapter>>()

    fun addBook(book: Book, bookChapters: List<Chapter> = emptyList()) {
        books[book.id] = book
        chapters[book.id] = bookChapters.sortedBy { it.number }.toMutableList()
    }

    fun removeBook(bookId: String) {
        books.remove(bookId)
        chapters.remove(bookId)
    }

    fun getBook(bookId: String): Book? = books[bookId]

    fun listBooks(): List<Book> = books.values.toList()

    fun listChapters(bookId: String): List<Chapter> =
        chapters[bookId]?.toList().orEmpty()

    fun markChapterRead(bookId: String, chapterId: String, progress: Float = 1f) {
        val list = chapters[bookId] ?: return
        val index = list.indexOfFirst { it.id == chapterId }
        if (index < 0) return
        val chapter = list[index]
        list[index] = chapter.copy(read = progress >= 1f, progress = progress.coerceIn(0f, 1f))
        val book = books[bookId] ?: return
        val finished = list.all { it.read }
        books[bookId] = book.copy(
            lastReadChapterId = chapterId,
            lastReadPosition = progress.coerceIn(0f, 1f),
            finished = finished,
        )
    }
}
