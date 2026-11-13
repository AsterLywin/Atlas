package com.atlas.core.source

/**
 * A content source: a catalogue (website, local folder, archive) that can list
 * books and their chapters. Mirrors the contract Mihon's `source-api` module
 * defines for extensions, kept deliberately small for Source v1: discovery and
 * chapter listing only, no network code yet.
 */
interface Source {
    /** Stable id, e.g. "local" or the catalogue host. */
    val id: String

    /** Display name shown in the source picker. */
    val name: String

    /** BCP-47 language tag of the catalogue content, e.g. "en", "fa", "ja". */
    val language: String

    /** Whether this source can list its catalogue (browse/search). */
    val supportsListing: Boolean

    /** Books this source currently offers, in catalogue order. */
    suspend fun listBooks(): List<com.atlas.core.library.Book>

    /** Chapters of one book, in reading order. */
    suspend fun listChapters(bookId: String): List<com.atlas.core.library.Chapter>

    /**
     * Books matching [query]. The default implementation filters [listBooks]
     * by title; network sources override it with a server-side search.
     */
    suspend fun searchBooks(query: String): List<com.atlas.core.library.Book> =
        listBooks().filter { it.title.contains(query, ignoreCase = true) }

    /**
     * Direct image URLs of one chapter, in reading order. The default is
     * empty (the source does not serve pages); network sources override it.
     */
    suspend fun listPages(chapterId: String): List<String> = emptyList()
}
