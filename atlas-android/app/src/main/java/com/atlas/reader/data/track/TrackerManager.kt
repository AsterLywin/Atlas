package com.atlas.reader.data.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.atlas.reader.data.track.anilist.Anilist
import com.atlas.reader.data.track.bangumi.Bangumi
import com.atlas.reader.data.track.comick.Comick
import com.atlas.reader.data.track.hikka.Hikka
import com.atlas.reader.data.track.kavita.Kavita
import com.atlas.reader.data.track.kitsu.Kitsu
import com.atlas.reader.data.track.komga.Komga
import com.atlas.reader.data.track.mangabaka.MangaBaka
import com.atlas.reader.data.track.mangaupdates.MangaUpdates
import com.atlas.reader.data.track.myanimelist.MyAnimeList
import com.atlas.reader.data.track.shikimori.Shikimori
import com.atlas.reader.data.track.suwayomi.Suwayomi
import kotlinx.coroutines.flow.combine

@Inject
@SingleIn(AppScope::class)
class TrackerManager {

    val myAnimeList = MyAnimeList(1L)
    val aniList = Anilist(2L)
    val kitsu = Kitsu(3L)
    val shikimori = Shikimori(4L)
    val bangumi = Bangumi(5L)
    val komga = Komga(6L)
    val mangaUpdates = MangaUpdates(7L)
    val kavita = Kavita(8L)
    val suwayomi = Suwayomi(9L)
    val hikka = Hikka(10L)
    val mangaBaka = MangaBaka(11L)
    val comick = Comick(12L)

    val trackers = listOf(
        mangaBaka,
        myAnimeList,
        aniList,
        kitsu,
        shikimori,
        bangumi,
        komga,
        mangaUpdates,
        kavita,
        suwayomi,
        hikka,
        comick,
    )

    fun loggedInTrackers() = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow() = combine(trackers.map { it.isLoggedInFlow }) {
        it.mapIndexedNotNull { index, isLoggedIn ->
            if (isLoggedIn) trackers[index] else null
        }
    }

    fun get(id: Long) = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>) = trackers.filter { it.id in ids }
}
