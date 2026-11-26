package atlas.domain.manga.model

import atlas.domain.manga.model.Manga
import com.atlas.reader.source.model.SManga

fun SManga.toDomainManga(sourceId: Long): Manga {
    return Manga.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        memo = memo,
        source = sourceId,
    )
}
