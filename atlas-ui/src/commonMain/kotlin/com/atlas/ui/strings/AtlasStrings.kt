package com.atlas.ui.strings

import com.atlas.ui.model.AppLanguage

/**
 * Every user-facing string of the shared UI, in Persian and English.
 *
 * This is a plain data class instead of CMP resources on purpose: the
 * language is a runtime setting (System / fa / en) that must switch
 * live on both platforms, and CMP resources resolve the locale from
 * the system only. Both languages are always present side by side,
 * so a missing translation is a compile-time shape error, not a
 * runtime fallback. A chapter is always a "بخش", never a "فصل".
 */
data class AtlasStrings(
    val appName: String,
    val tabLibrary: String,
    val tabBrowse: String,
    val tabSettings: String,
    val libraryEmpty: String,
    val browseEmpty: String,
    val searchHint: String,
    val search: String,
    val source: String,
    val addToLibrary: String,
    val removeFromLibrary: String,
    val inLibrary: String,
    val chaptersCount: String,
    val loading: String,
    val loadError: String,
    val retry: String,
    val coverDescription: String,
    val back: String,
    val chapterTitle: String,
    val pageIndicator: String,
    val readerModePager: String,
    val readerModeWebtoon: String,
    val nextChapter: String,
    val previousChapter: String,
    val settingsLanguage: String,
    val settingsLanguageSystem: String,
    val settingsLanguageFa: String,
    val settingsLanguageEn: String,
    val settingsTheme: String,
    val settingsThemeSystem: String,
    val settingsThemeLight: String,
    val settingsThemeDark: String,
    val settingsReaderMode: String,
    val noChapters: String,
    val markAsRead: String,
    val download: String,
    val downloadQueued: String,
    val downloadCancel: String,
    val downloaded: String,
    val downloadFailed: String,
    val downloadPercent: String,
    val settingsBackup: String,
    val backupCreate: String,
    val backupRestore: String,
    val backupCreated: String,
    val backupRestored: String,
    val backupRestoredSkipped: String,
    val backupCancelled: String,
    val backupFailed: String,
) {
    /** "%1$d chapters" style formatting, kept in the string itself. */
    fun chaptersCount(count: Int): String = chaptersCount.replace("%1\$d", count.toString())

    fun coverDescription(title: String): String = coverDescription.replace("%1\$s", title)

    fun pageIndicator(current: Int, total: Int): String =
        pageIndicator.replace("%1\$d", current.toString()).replace("%2\$d", total.toString())

    fun chapterTitle(numberText: String): String = chapterTitle.replace("%1\$s", numberText)

    fun downloadPercent(percent: Int): String = downloadPercent.replace("%1\$d", percent.toString())

    fun backupCreated(books: Int, fileName: String): String =
        backupCreated.replace("%1\$d", books.toString()).replace("%2\$s", fileName)

    fun backupRestored(books: Int, chapters: Int): String =
        backupRestored.replace("%1\$d", books.toString()).replace("%2\$d", chapters.toString())

    fun backupRestoredSkipped(books: Int, chapters: Int, skipped: Int): String =
        backupRestoredSkipped.replace("%1\$d", books.toString())
            .replace("%2\$d", chapters.toString()).replace("%3\$d", skipped.toString())

    fun backupFailed(reason: String): String = backupFailed.replace("%1\$s", reason)

    companion object {
        fun forLanguage(language: AppLanguage, systemIsPersian: Boolean): AtlasStrings =
            when (language) {
                AppLanguage.Fa -> persian
                AppLanguage.En -> english
                AppLanguage.System -> if (systemIsPersian) persian else english
            }

        val persian = AtlasStrings(
            appName = "اطلس",
            tabLibrary = "کتابخانه",
            tabBrowse = "مرور",
            tabSettings = "تنظیمات",
            libraryEmpty = "هنوز کتابی نیست. از «مرور» اضافه کن.",
            browseEmpty = "کتابی پیدا نشد.",
            searchHint = "جستجوی کتاب‌ها",
            search = "جستجو",
            source = "منبع",
            addToLibrary = "افزودن به کتابخانه",
            removeFromLibrary = "حذف از کتابخانه",
            inLibrary = "در کتابخانه",
            chaptersCount = "%1\$d بخش",
            loading = "در حال بارگذاری…",
            loadError = "بارگذاری ناموفق بود.",
            retry = "تلاش دوباره",
            coverDescription = "جلد %1\$s",
            back = "بازگشت",
            chapterTitle = "بخش %1\$s",
            pageIndicator = "صفحهٔ %1\$d از %2\$d",
            readerModePager = "ورق‌زن",
            readerModeWebtoon = "وب‌تون",
            nextChapter = "بخش بعد",
            previousChapter = "بخش قبل",
            settingsLanguage = "زبان",
            settingsLanguageSystem = "زبان دستگاه",
            settingsLanguageFa = "فارسی",
            settingsLanguageEn = "انگلیسی",
            settingsTheme = "تم",
            settingsThemeSystem = "تم دستگاه",
            settingsThemeLight = "روشن",
            settingsThemeDark = "تیره",
            settingsReaderMode = "حالت پیش‌فرض خواندن",
            noChapters = "این کتاب بخشی ندارد.",
            markAsRead = "خوانده شد",
            download = "دانلود",
            downloadQueued = "در صف دانلود",
            downloadCancel = "لغو",
            downloaded = "دانلود شد",
            downloadFailed = "دانلود ناموفق بود.",
            downloadPercent = "%1\$d٪",
            settingsBackup = "پشتیبان‌گیری",
            backupCreate = "ایجاد پشتیبان",
            backupRestore = "بازیابی از پشتیبان",
            backupCreated = "پشتیبان %1\$d کتاب در %2\$s ساخته شد.",
            backupRestored = "%1\$d کتاب و %2\$d بخش بازیابی شد.",
            backupRestoredSkipped = "%1\$d کتاب و %2\$d بخش بازیابی شد؛ %3\$d کتاب رد شد.",
            backupCancelled = "لغو شد.",
            backupFailed = "ناموفق بود: %1\$s",
        )

        val english = AtlasStrings(
            appName = "Atlas",
            tabLibrary = "Library",
            tabBrowse = "Browse",
            tabSettings = "Settings",
            libraryEmpty = "No books yet. Add some from Browse.",
            browseEmpty = "No books found.",
            searchHint = "Search books",
            search = "Search",
            source = "Source",
            addToLibrary = "Add to library",
            removeFromLibrary = "Remove from library",
            inLibrary = "In library",
            chaptersCount = "%1\$d chapters",
            loading = "Loading…",
            loadError = "Couldn't load.",
            retry = "Retry",
            coverDescription = "Cover of %1\$s",
            back = "Back",
            chapterTitle = "Chapter %1\$s",
            pageIndicator = "Page %1\$d of %2\$d",
            readerModePager = "Pager",
            readerModeWebtoon = "Webtoon",
            nextChapter = "Next chapter",
            previousChapter = "Previous chapter",
            settingsLanguage = "Language",
            settingsLanguageSystem = "System language",
            settingsLanguageFa = "Persian",
            settingsLanguageEn = "English",
            settingsTheme = "Theme",
            settingsThemeSystem = "System theme",
            settingsThemeLight = "Light",
            settingsThemeDark = "Dark",
            settingsReaderMode = "Default reading mode",
            noChapters = "This book has no chapters.",
            markAsRead = "Read",
            download = "Download",
            downloadQueued = "Queued for download",
            downloadCancel = "Cancel",
            downloaded = "Downloaded",
            downloadFailed = "Download failed.",
            downloadPercent = "%1\$d%",
            settingsBackup = "Backup",
            backupCreate = "Create backup",
            backupRestore = "Restore from backup",
            backupCreated = "Backed up %1\$d books to %2\$s.",
            backupRestored = "Restored %1\$d books and %2\$d chapters.",
            backupRestoredSkipped = "Restored %1\$d books and %2\$d chapters; %3\$d books skipped.",
            backupCancelled = "Cancelled.",
            backupFailed = "Failed: %1\$s",
        )
    }
}
