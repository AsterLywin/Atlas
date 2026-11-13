package com.atlas.domain.track.interactor

import dev.zacsweers.metro.Inject
import com.atlas.domain.track.model.toDbTrack
import com.atlas.reader.data.track.EnhancedTracker
import com.atlas.reader.data.track.Tracker
import logcat.LogPriority
import atlas.core.common.util.system.logcat
import atlas.domain.chapter.interactor.GetChaptersByMangaId
import atlas.domain.chapter.interactor.UpdateChapter
import atlas.domain.chapter.model.ChapterUpdate
import atlas.domain.track.interactor.UpsertTrack
import atlas.domain.track.model.Track
import kotlin.math.max

@Inject
class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val upsertTrack: UpsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        if (tracker !is EnhancedTracker) {
            return
        }

        val sortedChapters = getChaptersByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { ChapterUpdate(it.id) { read = true } }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val lastRead = max(remoteTrack.lastChapterRead, localLastRead.toDouble())
        val updatedTrack = remoteTrack.copy(lastChapterRead = lastRead)

        try {
            tracker.update(updatedTrack.toDbTrack())
            updateChapter.awaitAll(chapterUpdates)
            upsertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
