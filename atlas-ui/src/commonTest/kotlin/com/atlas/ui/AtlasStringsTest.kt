package com.atlas.ui

import com.atlas.ui.model.AppLanguage
import com.atlas.ui.strings.AtlasStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Both languages are always present, complete, and chapter-free of "فصل". */
class AtlasStringsTest {

    @Test
    fun `persian and english resolve by explicit language`() {
        assertEquals("کتابخانه", AtlasStrings.forLanguage(AppLanguage.Fa, false).tabLibrary)
        assertEquals("Library", AtlasStrings.forLanguage(AppLanguage.En, true).tabLibrary)
    }

    @Test
    fun `system follows the device language`() {
        assertEquals("کتابخانه", AtlasStrings.forLanguage(AppLanguage.System, true).tabLibrary)
        assertEquals("Library", AtlasStrings.forLanguage(AppLanguage.System, false).tabLibrary)
    }

    @Test
    fun `every string is non-blank in both languages`() {
        val fields = listOf(
            AtlasStrings.persian.appName to AtlasStrings.english.appName,
            AtlasStrings.persian.tabLibrary to AtlasStrings.english.tabLibrary,
            AtlasStrings.persian.tabBrowse to AtlasStrings.english.tabBrowse,
            AtlasStrings.persian.tabSettings to AtlasStrings.english.tabSettings,
            AtlasStrings.persian.libraryEmpty to AtlasStrings.english.libraryEmpty,
            AtlasStrings.persian.browseEmpty to AtlasStrings.english.browseEmpty,
            AtlasStrings.persian.searchHint to AtlasStrings.english.searchHint,
            AtlasStrings.persian.search to AtlasStrings.english.search,
            AtlasStrings.persian.source to AtlasStrings.english.source,
            AtlasStrings.persian.addToLibrary to AtlasStrings.english.addToLibrary,
            AtlasStrings.persian.removeFromLibrary to AtlasStrings.english.removeFromLibrary,
            AtlasStrings.persian.inLibrary to AtlasStrings.english.inLibrary,
            AtlasStrings.persian.chaptersCount to AtlasStrings.english.chaptersCount,
            AtlasStrings.persian.loading to AtlasStrings.english.loading,
            AtlasStrings.persian.loadError to AtlasStrings.english.loadError,
            AtlasStrings.persian.retry to AtlasStrings.english.retry,
            AtlasStrings.persian.coverDescription to AtlasStrings.english.coverDescription,
            AtlasStrings.persian.back to AtlasStrings.english.back,
            AtlasStrings.persian.chapterTitle to AtlasStrings.english.chapterTitle,
            AtlasStrings.persian.pageIndicator to AtlasStrings.english.pageIndicator,
            AtlasStrings.persian.readerModePager to AtlasStrings.english.readerModePager,
            AtlasStrings.persian.readerModeWebtoon to AtlasStrings.english.readerModeWebtoon,
            AtlasStrings.persian.nextChapter to AtlasStrings.english.nextChapter,
            AtlasStrings.persian.previousChapter to AtlasStrings.english.previousChapter,
            AtlasStrings.persian.settingsLanguage to AtlasStrings.english.settingsLanguage,
            AtlasStrings.persian.settingsLanguageSystem to AtlasStrings.english.settingsLanguageSystem,
            AtlasStrings.persian.settingsLanguageFa to AtlasStrings.english.settingsLanguageFa,
            AtlasStrings.persian.settingsLanguageEn to AtlasStrings.english.settingsLanguageEn,
            AtlasStrings.persian.settingsTheme to AtlasStrings.english.settingsTheme,
            AtlasStrings.persian.settingsThemeSystem to AtlasStrings.english.settingsThemeSystem,
            AtlasStrings.persian.settingsThemeLight to AtlasStrings.english.settingsThemeLight,
            AtlasStrings.persian.settingsThemeDark to AtlasStrings.english.settingsThemeDark,
            AtlasStrings.persian.settingsReaderMode to AtlasStrings.english.settingsReaderMode,
            AtlasStrings.persian.noChapters to AtlasStrings.english.noChapters,
            AtlasStrings.persian.markAsRead to AtlasStrings.english.markAsRead,
            AtlasStrings.persian.download to AtlasStrings.english.download,
            AtlasStrings.persian.downloadQueued to AtlasStrings.english.downloadQueued,
            AtlasStrings.persian.downloadCancel to AtlasStrings.english.downloadCancel,
            AtlasStrings.persian.downloaded to AtlasStrings.english.downloaded,
            AtlasStrings.persian.downloadFailed to AtlasStrings.english.downloadFailed,
            AtlasStrings.persian.downloadPercent to AtlasStrings.english.downloadPercent,
            AtlasStrings.persian.settingsBackup to AtlasStrings.english.settingsBackup,
            AtlasStrings.persian.backupCreate to AtlasStrings.english.backupCreate,
            AtlasStrings.persian.backupRestore to AtlasStrings.english.backupRestore,
            AtlasStrings.persian.backupCreated to AtlasStrings.english.backupCreated,
            AtlasStrings.persian.backupRestored to AtlasStrings.english.backupRestored,
            AtlasStrings.persian.backupRestoredSkipped to AtlasStrings.english.backupRestoredSkipped,
            AtlasStrings.persian.backupCancelled to AtlasStrings.english.backupCancelled,
            AtlasStrings.persian.backupFailed to AtlasStrings.english.backupFailed,
        )
        for ((fa, en) in fields) {
            assertTrue(fa.isNotBlank(), "blank Persian string")
            assertTrue(en.isNotBlank(), "blank English string: $en")
        }
    }

    @Test
    fun `persian strings never call a chapter a فصل`() {
        val persian = AtlasStrings.persian
        val all = listOf(
            persian.chaptersCount,
            persian.chapterTitle,
            persian.nextChapter,
            persian.previousChapter,
            persian.noChapters,
        ).joinToString(" ")
        assertFalse(all.contains("فصل"), "found فصل in: $all")
        assertTrue(all.contains("بخش"), "expected بخش in: $all")
    }

    @Test
    fun `formatting helpers fill the placeholders`() {
        assertEquals("3 بخش", AtlasStrings.persian.chaptersCount(3))
        assertEquals("3 chapters", AtlasStrings.english.chaptersCount(3))
        assertEquals("بخش 1.5", AtlasStrings.persian.chapterTitle("1.5"))
        assertEquals("Page 2 of 10", AtlasStrings.english.pageIndicator(2, 10))
        assertEquals("صفحهٔ 2 از 10", AtlasStrings.persian.pageIndicator(2, 10))
        assertEquals("Cover of Dune", AtlasStrings.english.coverDescription("Dune"))
        assertEquals("42٪", AtlasStrings.persian.downloadPercent(42))
        assertEquals("42%", AtlasStrings.english.downloadPercent(42))
        assertEquals(
            "پشتیبان 2 کتاب در Atlas_2026-11-27_10-00.tachibk ساخته شد.",
            AtlasStrings.persian.backupCreated(2, "Atlas_2026-11-27_10-00.tachibk"),
        )
        assertEquals(
            "Restored 2 books and 5 chapters.",
            AtlasStrings.english.backupRestored(2, 5),
        )
        assertEquals(
            "Restored 2 books and 5 chapters; 1 books skipped.",
            AtlasStrings.english.backupRestoredSkipped(2, 5, 1),
        )
        assertEquals("ناموفق بود: boom", AtlasStrings.persian.backupFailed("boom"))
    }
}
