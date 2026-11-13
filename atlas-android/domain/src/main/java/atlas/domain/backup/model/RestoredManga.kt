package atlas.domain.backup.model

import atlas.domain.chapter.model.Chapter
import atlas.domain.manga.model.Manga
import atlas.domain.track.model.Track
import kotlin.time.Instant

data class RestoredManga(
    val manga: Manga,
    val chapters: List<Chapter>,
    val categoryIds: List<Long>,
    val history: List<RestoredHistory>,
    val tracks: List<Track>,
    val excludedScanlators: List<String>,
)

data class RestoredHistory(
    val chapterUrl: String,
    val readAt: Instant?,
    val readDuration: Long,
)
