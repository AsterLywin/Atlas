package com.atlas.core.download

/** Download state of one chapter. Kept in the core so Android and desktop share it. */
sealed interface DownloadState {
    /** Never downloaded. */
    data object NotDownloaded : DownloadState

    /** Waiting for a free download slot. */
    data object Queued : DownloadState

    /** Downloading; [progress] is 0..1. */
    data class Downloading(val progress: Float) : DownloadState

    /** Fully downloaded and stored locally. */
    data object Downloaded : DownloadState

    /** The last attempt failed; [reason] is a short human-readable cause. */
    data class Failed(val reason: String) : DownloadState
}

/** Minimal download request: which chapter, from which source, to where. */
data class DownloadRequest(
    val chapterId: String,
    val bookId: String,
    val sourceId: String,
)
