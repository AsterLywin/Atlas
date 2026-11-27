package com.atlas.ui.state

/**
 * The result of one backup or restore action, as the shared UI sees it.
 * The platform shell performs the action (file picker included) and
 * reports back; the UI only turns the outcome into strings.
 */
sealed interface BackupOutcome {
    /** A backup file was written; [fileName] is just the file name, not a path. */
    data class Created(val books: Int, val fileName: String) : BackupOutcome

    /** A backup was restored; [skipped] counts books whose source is unknown. */
    data class Restored(val books: Int, val chapters: Int, val skipped: Int) : BackupOutcome

    /** The user closed the file picker without choosing a file. */
    data object Cancelled : BackupOutcome

    /** The action failed; [reason] is a short human-readable cause. */
    data class Failed(val reason: String) : BackupOutcome
}

/**
 * Backup and restore of the library as a `.tachibk` file, in the exact
 * format the Android app writes. Only platforms that implement it show
 * the backup section in Settings; the others pass null and hide it.
 */
interface BackupActions {
    /** Ask where to write the backup, then write it. */
    suspend fun createBackup(): BackupOutcome

    /** Ask which backup to read, then restore it into the library. */
    suspend fun restoreBackup(): BackupOutcome
}
