package com.atlas.desktop

import com.atlas.core.library.Book
import com.atlas.core.library.Chapter
import com.atlas.desktop.backup.Backup
import com.atlas.desktop.backup.BackupChapter
import com.atlas.desktop.backup.BackupManga
import com.atlas.desktop.backup.BackupSource
import com.atlas.desktop.backup.DesktopBackupCreator
import com.atlas.desktop.backup.DesktopBackupRestorer
import com.atlas.desktop.backup.kotatsuSourceId
import com.atlas.desktop.backup.readBackupFile
import com.atlas.desktop.backup.sourceById
import com.atlas.desktop.backup.sourceId
import com.atlas.desktop.backup.writeBackupFile
import com.atlas.desktop.db.DesktopLibraryStore
import com.atlas.parsers.KotatsuParserRegistry
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `.tachibk` round-trip on desktop: a real backup file is written from
 * the library database, then restored into a fresh database, and the
 * library comes back whole. The stream framing (gzip + protobuf) is
 * the Android format, so the same file restores on Android too.
 */
class DesktopBackupTest {

    private lateinit var tempDir: File
    private val registry = KotatsuParserRegistry()

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-backup-test-${System.nanoTime()}")
            .also { it.mkdirs() }
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun seedLibrary(store: DesktopLibraryStore) {
        val otherSource = registry.sources.first { it.name != "MANGADEX" }
        store.addBook(
            Book(
                id = "kotatsu:MANGADEX:1:aHR0cHM6Ly9leGFtcGxlLmNvbS9tYW5nYS8x",
                title = "Fake Manga",
                author = "Author A",
                sourceId = "kotatsu:MANGADEX",
                coverUrl = "https://example.com/cover.jpg",
            ),
            listOf(
                Chapter(id = "ch1", bookId = "b", title = "First", number = 1f, read = true, progress = 1f),
                Chapter(id = "ch1.5", bookId = "b", title = "Half", number = 1.5f),
                Chapter(id = "ch2", bookId = "b", title = "Second", number = 2f, progress = 0.5f),
            ),
        )
        store.addBook(
            Book(id = "kotatsu:${otherSource.name}:2:dXJs", title = "Second Book", sourceId = "kotatsu:${otherSource.name}"),
            listOf(
                Chapter(id = "chA", bookId = "b2", title = "One", number = 1f),
            ),
        )
    }

    @Test
    fun `backup round-trip restores the library on a fresh database`() {
        val backupFile = tempDir.resolve("atlas.tachibk")
        DesktopLibraryStore.open(tempDir.resolve("first.db")).use { store ->
            seedLibrary(store)
            val created = DesktopBackupCreator(store, registry).createBackup(backupFile)
            assertEquals(2, created.books)
            assertTrue(created.skipped.isEmpty())
        }

        // A real .tachibk file: gzip magic first.
        val magic = backupFile.inputStream().use { it.readNBytes(2) }
        assertEquals(0x1f.toByte(), magic[0])
        assertEquals(0x8b.toByte(), magic[1])

        DesktopLibraryStore.open(tempDir.resolve("fresh.db")).use { fresh ->
            val report = DesktopBackupRestorer(fresh, registry).restore(backupFile)
            assertEquals(2, report.books)
            assertEquals(4, report.chapters)
            assertTrue(report.skipped.isEmpty())

            val books = fresh.listBooks()
            assertEquals(2, books.size)
            val manga = books.single { it.title == "Fake Manga" }
            assertEquals("Author A", manga.author)
            assertEquals("kotatsu:MANGADEX", manga.sourceId)
            assertEquals("https://example.com/cover.jpg", manga.coverUrl)
            val chapters = fresh.listChapters(manga.id)
            assertEquals(3, chapters.size)
            assertEquals(1.5f, chapters[1].number)
            assertTrue(chapters[0].read)
            assertEquals(1f, chapters[0].progress)
            // Progress is a page index in the format, so only the read
            // flag survives: a half-read chapter restores as unread.
            assertEquals(0f, chapters[2].progress)
        }
    }

    @Test
    fun `backup metadata carries the numeric source ids android uses`() {
        val backupFile = tempDir.resolve("atlas.tachibk")
        DesktopLibraryStore.open(tempDir.resolve("first.db")).use { store ->
            seedLibrary(store)
            DesktopBackupCreator(store, registry).createBackup(backupFile)
        }
        val contents = readBackupFile(backupFile)
        assertEquals(2, contents.entries.size)
        val mangadex = contents.metadata.backupSources.single { it.name == "MangaDex" }
        assertEquals(3963947406810851516L, mangadex.sourceId)
        assertEquals(3963947406810851516L, kotatsuSourceId("MANGADEX", ""))
        val entry = contents.entries.single { it.title == "Fake Manga" }
        assertEquals(3963947406810851516L, entry.source)
        assertTrue(entry.favorite)
        assertEquals(3, entry.chapters.size)
        assertEquals(1.5f, entry.chapters[1].chapterNumber)
    }

    @Test
    fun `an android-style backup restores on desktop`() {
        // As the Android creator would write it: entries + sources metadata.
        val file = tempDir.resolve("android.tachibk")
        val otherSource = registry.sources.first { it.name != "MANGADEX" }
        writeBackupFile(
            file,
            listOf(
                BackupManga(
                    source = kotatsuSourceId("MANGADEX", ""),
                    url = "kotatsu:MANGADEX:9:aHR0cHM6Ly9leGFtcGxlLmNvbS9tLzE",
                    title = "Android Book",
                    author = "Android Author",
                    thumbnailUrl = "https://example.com/a.jpg",
                    chapters = listOf(
                        BackupChapter(url = "c9", name = "Ch 1", chapterNumber = 1f, read = true),
                        BackupChapter(url = "c10", name = "Ch 2", chapterNumber = 2f),
                    ),
                ),
                BackupManga(
                    source = registry.sourceId(otherSource),
                    url = "kotatsu:${otherSource.name}:3:eA",
                    title = "Other Book",
                ),
            ),
            Backup(
                backupSources = listOf(
                    BackupSource(name = "MangaDex", sourceId = kotatsuSourceId("MANGADEX", "")),
                    BackupSource(name = otherSource.title, sourceId = registry.sourceId(otherSource)),
                ),
            ),
        )

        DesktopLibraryStore.open(tempDir.resolve("fresh.db")).use { fresh ->
            val report = DesktopBackupRestorer(fresh, registry).restore(file)
            assertEquals(2, report.books)
            assertEquals(2, report.chapters)
            val book = fresh.listBooks().single { it.title == "Android Book" }
            assertEquals("kotatsu:MANGADEX", book.sourceId)
            assertEquals("Android Author", book.author)
            assertTrue(fresh.listChapters(book.id).single { it.number == 1f }.read)
        }
    }

    @Test
    fun `books from unknown sources are skipped and reported`() {
        val file = tempDir.resolve("old-mihon.tachibk")
        writeBackupFile(
            file,
            listOf(
                BackupManga(
                    source = 999999L,
                    url = "/manga/old",
                    title = "Old Mihon Manga",
                    chapters = listOf(
                        BackupChapter(url = "/chapter/1", name = "Chapter 1", chapterNumber = 1f),
                    ),
                ),
            ),
            Backup(
                backupSources = listOf(BackupSource(name = "Old Extension", sourceId = 999999L)),
            ),
        )

        DesktopLibraryStore.open(tempDir.resolve("fresh.db")).use { fresh ->
            val report = DesktopBackupRestorer(fresh, registry).restore(file)
            assertEquals(0, report.books)
            assertEquals(listOf("Old Mihon Manga"), report.skipped)
            assertTrue(fresh.listBooks().isEmpty())
        }
    }

    @Test
    fun `restoring over an existing library keeps the local read flags`() {
        val backupFile = tempDir.resolve("atlas.tachibk")
        DesktopLibraryStore.open(tempDir.resolve("first.db")).use { store ->
            seedLibrary(store)
            DesktopBackupCreator(store, registry).createBackup(backupFile)
        }
        DesktopLibraryStore.open(tempDir.resolve("second.db")).use { store ->
            // The same book already here, chapter 2 read locally.
            store.addBook(
                Book(
                    id = "kotatsu:MANGADEX:1:aHR0cHM6Ly9leGFtcGxlLmNvbS9tYW5nYS8x",
                    title = "Fake Manga",
                    sourceId = "kotatsu:MANGADEX",
                ),
                listOf(
                    Chapter(id = "ch2", bookId = "b", title = "Second", number = 2f, read = true, progress = 1f),
                ),
            )
            val report = DesktopBackupRestorer(store, registry).restore(backupFile)
            assertEquals(2, report.books)
            val chapters = store.listChapters("kotatsu:MANGADEX:1:aHR0cHM6Ly9leGFtcGxlLmNvbS9tYW5nYS8x")
            assertEquals(3, chapters.size)
            assertTrue(chapters.single { it.number == 2f }.read)
            assertEquals(1f, chapters.single { it.number == 2f }.progress)
        }
    }

    @Test
    fun `an empty library refuses to back up`() {
        DesktopLibraryStore.open(tempDir.resolve("empty.db")).use { store ->
            assertThrows(IllegalStateException::class.java) {
                DesktopBackupCreator(store, registry).createBackup(tempDir.resolve("atlas.tachibk"))
            }
        }
    }

    @Test
    fun `a malformed backup file fails the restore`() {
        val bad = tempDir.resolve("bad.tachibk").also { it.writeText("not a backup at all") }
        DesktopLibraryStore.open(tempDir.resolve("fresh.db")).use { fresh ->
            assertThrows(java.io.IOException::class.java) {
                DesktopBackupRestorer(fresh, registry).restore(bad)
            }
        }
    }

    @Test
    fun `a missing source id resolves to null`() {
        assertEquals(null, registry.sourceById(999999L))
        assertEquals("MANGADEX", registry.sourceById(3963947406810851516L)?.name)
    }
}
