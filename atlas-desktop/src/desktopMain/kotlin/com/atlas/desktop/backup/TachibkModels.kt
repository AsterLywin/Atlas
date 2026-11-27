package com.atlas.desktop.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * The `.tachibk` backup models, field-for-field the Android app's
 * [com.atlas.reader.data.backup.models] (Mihon's format, protobuf).
 *
 * The numbers are the wire format: they must match Android exactly,
 * or a desktop backup will not restore on Android and vice versa.
 * Only the fields the desktop reads or writes are declared here —
 * preferences, source preferences and extension stores are NOT in this
 * model (the desktop has none of those); protobuf decoders skip
 * unknown fields, so an Android backup carrying them still decodes,
 * and a desktop backup without them still restores on Android.
 *
 * `memo` is the JSON object Android stores per manga/chapter, as raw
 * bytes; the desktop writes the empty object and ignores it on read.
 */
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
)

@Serializable
class BackupManga(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(14) var viewer: Int = 0,
    @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewer_flags: Int? = null,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var memo: ByteArray = EMPTY_JSON_OBJECT,
    @ProtoNumber(113) var nextUpdate: Long = 0,
)

@Serializable
class BackupChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    @ProtoNumber(6) var lastPageRead: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(13) var memo: ByteArray = EMPTY_JSON_OBJECT,
)

@Serializable
data class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    @ProtoNumber(100) var flags: Long = 0,
)

@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var sourceId: Long,
)

@Serializable
data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
)

@Serializable
data class BackupTracking(
    @ProtoNumber(1) var syncId: Int,
    @ProtoNumber(2) var libraryId: Long,
    @ProtoNumber(3) var mediaIdInt: Int = 0,
    @ProtoNumber(4) var trackingUrl: String = "",
    @ProtoNumber(5) var title: String = "",
    @ProtoNumber(6) var lastChapterRead: Float = 0F,
    @ProtoNumber(7) var totalChapters: Int = 0,
    @ProtoNumber(8) var score: Float = 0F,
    @ProtoNumber(9) var status: Int = 0,
    @ProtoNumber(10) var startedReadingDate: Long = 0,
    @ProtoNumber(11) var finishedReadingDate: Long = 0,
    @ProtoNumber(12) var private: Boolean = false,
    @ProtoNumber(100) var mediaId: Long = 0,
)

/** Same two values, same order, as Android's `UpdateStrategy`. */
@Serializable
enum class UpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}

private val EMPTY_JSON_OBJECT: ByteArray = "{}".encodeToByteArray()
