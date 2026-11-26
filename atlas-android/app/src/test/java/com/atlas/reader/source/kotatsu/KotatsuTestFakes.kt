package com.atlas.reader.source.kotatsu

import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN

/** A minimal Kotatsu source for tests. */
internal class FakeSource(
    name: String = "FAKESOURCE",
    title: String = "Fake Source",
    locale: String = "en",
) : ParserMangaSource(name, title, locale, ContentType.MANGA, false)

internal fun fakeManga(id: Long, title: String, source: ParserMangaSource) = Manga(
    id = id,
    title = title,
    altTitles = emptySet(),
    url = "/manga/$id",
    publicUrl = "https://fake.example/manga/$id",
    rating = RATING_UNKNOWN,
    contentRating = null,
    coverUrl = "https://fake.example/cover/$id.jpg",
    tags = setOf(MangaTag("Action", "action", source)),
    state = MangaState.ONGOING,
    authors = setOf("Author $id"),
    largeCoverUrl = "https://fake.example/large/$id.jpg",
    description = "Description $id",
    chapters = null,
    source = source,
)

internal fun fakeChapter(id: Long, number: Float, source: ParserMangaSource) = MangaChapter(
    id = id,
    title = "Chapter $number",
    number = number,
    volume = 0,
    url = "/manga/1/chapter/$id",
    scanlator = "Scan Team",
    uploadDate = 1_700_000_000_000L,
    branch = null,
    source = source,
)

internal fun fakePage(id: Long, url: String, source: ParserMangaSource) = MangaPage(
    id = id,
    url = url,
    preview = null,
    source = source,
)
