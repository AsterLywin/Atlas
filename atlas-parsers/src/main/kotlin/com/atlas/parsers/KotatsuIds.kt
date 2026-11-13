package com.atlas.parsers

import java.util.Base64
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN

/**
 * Stateless ids for books and chapters coming from Kotatsu parsers.
 *
 * A parser hands back [Manga]/[MangaChapter] objects, but the Atlas
 * [com.atlas.core.source.Source] contract only passes string ids around.
 * Instead of keeping a cache that can go stale, the ids carry everything
 * needed to rebuild the minimal parser objects:
 *
 *   book id:    `kotatsu:<SOURCE_NAME>:<mangaId>:<base64url(relativeUrl)>`
 *   chapter id: `<book id>:<chapterId>:<base64url(chapterUrl)>`
 *
 * The relative URLs are Base64-URL encoded (no padding) because they may
 * contain `/`, `?`, `:` and non-ASCII characters. Source names are
 * `[A-Z0-9_]+` and manga/chapter ids are numbers, so plain `:` splitting
 * is unambiguous.
 */
object KotatsuIds {

    private const val PREFIX = "kotatsu"

    fun bookIdOf(source: ParserMangaSource, manga: Manga): String {
        val url = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(manga.url.toByteArray(Charsets.UTF_8))
        return "$PREFIX:${source.name}:${manga.id}:$url"
    }

    fun chapterIdOf(bookId: String, chapter: MangaChapter): String {
        val url = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(chapter.url.toByteArray(Charsets.UTF_8))
        return "$bookId:${chapter.id}:$url"
    }

    /** Parsed book id, or null when [bookId] is not a Kotatsu book id. */
    fun parseBookId(bookId: String): ParsedBookId? {
        val parts = bookId.split(":")
        if (parts.size != 4 || parts[0] != PREFIX) return null
        val mangaId = parts[2].toLongOrNull() ?: return null
        val url = runCatching {
            Base64.getUrlDecoder().decode(parts[3]).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        return ParsedBookId(sourceName = parts[1], mangaId = mangaId, url = url)
    }

    /** Parsed chapter id, or null when [chapterId] is not a Kotatsu chapter id. */
    fun parseChapterId(chapterId: String): ParsedChapterId? {
        val parts = chapterId.split(":")
        if (parts.size != 6 || parts[0] != PREFIX) return null
        val book = parseBookId(parts.subList(0, 4).joinToString(":")) ?: return null
        val id = parts[4].toLongOrNull() ?: return null
        val url = runCatching {
            Base64.getUrlDecoder().decode(parts[5]).toString(Charsets.UTF_8)
        }.getOrNull() ?: return null
        return ParsedChapterId(book = book, chapterId = id, url = url)
    }

    /**
     * Rebuild the minimal [Manga] a parser needs for [MangaParser.getDetails]:
     * only id, url and source are read by parsers for a details call.
     */
    fun MangaSource.toManga(parsed: ParsedBookId): Manga = Manga(
        id = parsed.mangaId,
        title = "",
        altTitles = emptySet(),
        url = parsed.url,
        publicUrl = parsed.url,
        rating = RATING_UNKNOWN,
        contentRating = null,
        coverUrl = null,
        tags = emptySet(),
        state = null,
        authors = emptySet(),
        source = this,
    )

    /** Rebuild the minimal [MangaChapter] a parser needs for `getPages`. */
    fun MangaSource.toChapter(parsed: ParsedChapterId): MangaChapter = MangaChapter(
        id = parsed.chapterId,
        title = null,
        number = 0f,
        volume = 0,
        url = parsed.url,
        scanlator = null,
        uploadDate = 0L,
        branch = null,
        source = this,
    )

    data class ParsedBookId(
        val sourceName: String,
        val mangaId: Long,
        val url: String,
    )

    data class ParsedChapterId(
        val book: ParsedBookId,
        val chapterId: Long,
        val url: String,
    )
}
