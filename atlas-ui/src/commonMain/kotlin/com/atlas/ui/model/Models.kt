package com.atlas.ui.model

/**
 * The shared UI's own models. They deliberately do NOT reuse the core
 * [com.atlas.core.library.Book] / [com.atlas.core.library.Chapter] types:
 * atlas-core is a JVM-only module and cannot be a commonMain dependency,
 * so the platform shells map their own book/chapter types onto these.
 * The fields mirror the core ones, including the Float chapter number
 * (fractional chapters such as 1.5 are real catalogue numbers).
 */
data class UiBook(
    val id: String,
    val title: String,
    val author: String? = null,
    val sourceId: String,
    val coverUrl: String? = null,
)

/** One chapter (a "بخش") of a book, in reading order. */
data class UiChapter(
    val id: String,
    val bookId: String,
    val title: String,
    val number: Float,
    val read: Boolean = false,
    /** 0..1 fraction read inside this chapter. */
    val progress: Float = 0f,
)

/** A catalogue source shown in the source picker. */
data class UiSource(
    val id: String,
    val title: String,
)

/** How the reader shows pages. */
enum class ReaderMode {
    Pager,
    Webtoon,
}

/** The UI language. System follows the device locale. */
enum class AppLanguage {
    System,
    Fa,
    En,
}

/** The UI theme. System follows the device theme. */
enum class AppTheme {
    System,
    Light,
    Dark,
}

/** Persisted user settings (language, theme, default reader mode). */
data class AppSettings(
    val language: AppLanguage = AppLanguage.System,
    val theme: AppTheme = AppTheme.System,
    val readerMode: ReaderMode = ReaderMode.Pager,
)
