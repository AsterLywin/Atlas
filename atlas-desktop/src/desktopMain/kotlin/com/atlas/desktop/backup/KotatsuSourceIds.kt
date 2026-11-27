package com.atlas.desktop.backup

import com.atlas.parsers.KotatsuParserRegistry
import java.security.MessageDigest
import org.koitharu.kotatsu.parsers.model.ParserMangaSource

/**
 * The numeric source id a `.tachibk` backup carries, byte-for-byte the
 * Android bridge's id: `HttpSource.generateId("kotatsu:<ENUM_NAME>",
 * locale, 1)` — MD5 of `"<name-lowercase>/<lang>/<versionId>"`, first
 * 8 bytes big-endian, sign bit cleared. The name is the parser's enum
 * key (e.g. `MANGADEX`), never the display title, and the locale is
 * the raw source locale (empty for MangaDex) — exactly as
 * [com.atlas.reader.source.kotatsu.KotatsuHttpSource] computes it.
 * An id must never change once a manga is saved: the library and
 * backups key off it.
 */
fun kotatsuSourceId(name: String, locale: String): Long {
    val key = "kotatsu:${name.lowercase()}/$locale/1"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
    return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}

/** The `.tachibk` source id of a registered Kotatsu source. */
fun KotatsuParserRegistry.sourceId(source: ParserMangaSource): Long =
    kotatsuSourceId(source.name, source.locale)

/**
 * The registered source for a `.tachibk` source id, or null when no
 * installed source has it (an old Mihon backup from an extension this
 * install does not have). Broken sources are included: a book from one
 * still restores, it just cannot update afterwards.
 */
fun KotatsuParserRegistry.sourceById(id: Long): ParserMangaSource? =
    allSources.firstOrNull { sourceId(it) == id }
