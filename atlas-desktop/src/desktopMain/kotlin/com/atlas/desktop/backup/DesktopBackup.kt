package com.atlas.desktop.backup

import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.parsers.KotatsuParserRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The result of writing a backup file. */
data class BackupFile(
    val file: File,
    /** Books written (books from unknown sources are skipped, see [skipped]). */
    val books: Int,
    /** Titles skipped because their source is not registered. */
    val skipped: List<String>,
)

/** The result of restoring a backup file. */
data class BackupRestoreReport(
    /** Books inserted or updated in the library. */
    val books: Int,
    /** Chapters inserted or updated. */
    val chapters: Int,
    /** Titles skipped because their source is not registered. */
    val skipped: List<String>,
)

/**
 * Writes the desktop library as a `.tachibk` file in the exact format
 * the Android app writes (see [writeBackupFile]): books with their
 * chapters, read flags and sources. The desktop has no description,
 * genre, status, history, tracking, categories or preferences to
 * carry, so those fields are written empty — an Android restore of a
 * desktop backup still reads it, and a desktop restore of an Android
 * backup ignores those fields.
 *
 * Progress is a 0..1 fraction on desktop but a page index in the
 * format, so only the read flag survives a backup round-trip; a
 * half-read chapter restores as unread with its local progress kept
 * when re-restoring over an existing library.
 */
class DesktopBackupCreator(
    private val store: DesktopLibraryStore,
    private val registry: KotatsuParserRegistry,
) {
    /** Writes the backup to [destFile]; throws when the library is empty. */
    fun createBackup(destFile: File): BackupFile {
        val books = store.listBooks()
        if (books.isEmpty()) throw IllegalStateException("Library is empty, nothing to back up")
        val entries = mutableListOf<BackupManga>()
        val sourceIds = linkedSetOf<Long>()
        val skipped = mutableListOf<String>()
        for (book in books) {
            val source = registry.findSource(book.sourceId.removePrefix("kotatsu:"))
            if (source == null) {
                skipped.add(book.title)
                continue
            }
            val numericId = registry.sourceId(source)
            sourceIds += numericId
            val chapters = store.listChapters(book.id).mapIndexed { index, chapter ->
                BackupChapter(
                    url = chapter.id,
                    name = chapter.title,
                    chapterNumber = chapter.number,
                    read = chapter.read,
                    sourceOrder = index.toLong(),
                )
            }
            entries += BackupManga(
                source = numericId,
                url = book.id,
                title = book.title,
                author = book.author,
                thumbnailUrl = book.coverUrl,
                favorite = true,
                chapters = chapters,
            )
        }
        val metadata = Backup(
            backupSources = sourceIds.map { id ->
                val source = registry.sourceById(id)
                    ?: error("Source $id was just resolved")
                BackupSource(name = source.title, sourceId = id)
            },
        )
        writeBackupFile(destFile, entries, metadata)
        return BackupFile(destFile, entries.size, skipped)
    }
}

/**
 * Restores a `.tachibk` file into the desktop library — a desktop
 * backup or an Android one, the format is the same. Books are matched
 * by their URL id; restoring over an existing library keeps the local
 * read flag and the further of the two progress fractions.
 * Books whose source is not installed are skipped and reported, not
 * stubbed: the desktop has no stub-source store.
 */
class DesktopBackupRestorer(
    private val store: DesktopLibraryStore,
    private val registry: KotatsuParserRegistry,
) {
    /** Restores [backupFile] into the library; throws on a malformed file. */
    fun restore(backupFile: File): BackupRestoreReport {
        val contents = readBackupFile(backupFile)
        var books = 0
        var chapters = 0
        val skipped = mutableListOf<String>()
        for (entry in contents.entries) {
            val source = registry.sourceById(entry.source)
            if (source == null) {
                skipped.add(entry.title)
                continue
            }
            val bookId = entry.url
            val existing = store.listChapters(bookId).associateBy { it.id }
            val mergedChapters = entry.chapters.map { backupChapter ->
                val previous = existing[backupChapter.url]
                Chapter(
                    id = backupChapter.url,
                    bookId = bookId,
                    title = backupChapter.name,
                    number = backupChapter.chapterNumber,
                    read = backupChapter.read || previous?.read == true,
                    progress = maxOf(
                        if (backupChapter.read) 1f else 0f,
                        previous?.progress ?: 0f,
                    ),
                )
            }
            store.addBook(
                Book(
                    id = bookId,
                    title = entry.title,
                    author = entry.author,
                    sourceId = "kotatsu:${source.name}",
                    coverUrl = entry.thumbnailUrl,
                ),
                mergedChapters,
            )
            books++
            chapters += mergedChapters.size
        }
        return BackupRestoreReport(books, chapters, skipped)
    }
}

/** `Atlas_yyyy-MM-dd_HH-mm.tachibk`, the Android filename scheme with the Atlas id. */
fun backupFileName(now: Date = Date()): String {
    val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(now)
    return "Atlas_$date.tachibk"
}
