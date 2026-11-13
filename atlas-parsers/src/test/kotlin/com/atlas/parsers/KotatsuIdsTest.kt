package com.atlas.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN

class KotatsuIdsTest {

    private val source: MangaParserSource = MangaParserSource.MANGADEX

    private fun manga(url: String) = Manga(
        id = 42L,
        title = "Test",
        altTitles = emptySet(),
        url = url,
        publicUrl = url,
        rating = RATING_UNKNOWN,
        contentRating = null,
        coverUrl = null,
        tags = emptySet(),
        state = null,
        authors = emptySet(),
        source = source,
    )

    private fun chapter(url: String) = MangaChapter(
        id = 7L,
        title = "Ch 1",
        number = 1f,
        volume = 0,
        url = url,
        scanlator = null,
        uploadDate = 0L,
        branch = null,
        source = source,
    )

    @Test
    fun `book id round-trips urls with slashes queries and unicode`() {
        val url = "/manga/one-piece-1112?lang=en&x=ü"
        val id = KotatsuIds.bookIdOf(source, manga(url))
        val parsed = KotatsuIds.parseBookId(id)
        assertEquals("MANGADEX", parsed?.sourceName)
        assertEquals(42L, parsed?.mangaId)
        assertEquals(url, parsed?.url)
    }

    @Test
    fun `chapter id round-trips and keeps the book part`() {
        val bookId = KotatsuIds.bookIdOf(source, manga("/manga/42"))
        val chapterId = KotatsuIds.chapterIdOf(bookId, chapter("/manga/42/ch-7?ref=a:b"))
        val parsed = KotatsuIds.parseChapterId(chapterId)
        assertEquals(7L, parsed?.chapterId)
        assertEquals("/manga/42/ch-7?ref=a:b", parsed?.url)
        assertEquals(42L, parsed?.book?.mangaId)
        // Rebuilding the minimal objects keeps what parsers read.
        val p = parsed!!
        val rebuiltManga = with(KotatsuIds) { source.toManga(p.book) }
        assertEquals("/manga/42", rebuiltManga.url)
        val rebuiltChapter = with(KotatsuIds) { source.toChapter(p) }
        assertEquals("/manga/42/ch-7?ref=a:b", rebuiltChapter.url)
    }

    @Test
    fun `foreign ids are rejected`() {
        assertNull(KotatsuIds.parseBookId("local:abc"))
        assertNull(KotatsuIds.parseBookId("kotatsu:MANGADEX:not-a-number:AAAA"))
        assertNull(KotatsuIds.parseChapterId("kotatsu:MANGADEX:42:AAAA:7"))
    }
}
