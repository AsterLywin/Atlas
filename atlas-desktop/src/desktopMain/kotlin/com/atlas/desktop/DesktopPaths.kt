package com.atlas.desktop

import java.io.File

/**
 * Where the desktop app keeps its files, per OS convention:
 * Linux `~/.local/share/Atlas` (or `$XDG_DATA_HOME/Atlas`), macOS
 * `~/Library/Application Support/Atlas`, Windows `%APPDATA%/Atlas`.
 */
fun atlasDataDir(): File {
    val os = System.getProperty("os.name").lowercase()
    val home = File(System.getProperty("user.home"))
    return when {
        os.contains("win") -> {
            val roaming = System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").path
            File(roaming, "Atlas")
        }
        os.contains("mac") -> home.resolve("Library/Application Support/Atlas")
        else -> {
            val xdg = System.getenv("XDG_DATA_HOME") ?: home.resolve(".local/share").path
            File(xdg, "Atlas")
        }
    }
}

/** The library database file: `<data dir>/atlas.db`. */
fun atlasDbFile(): File = atlasDataDir().resolve("atlas.db")

/** On-disk image cache dir (Coil disk cache): `<data dir>/cache/images`. */
fun atlasImageCacheDir(): File = atlasDataDir().resolve("cache/images")

/** Downloaded chapters: `<data dir>/downloads/<book>/<chapter>/NNN.ext`. */
fun atlasDownloadsDir(): File = atlasDataDir().resolve("downloads")

/** Library backups (`.tachibk` files): `<data dir>/backups`. */
fun atlasBackupDir(): File = atlasDataDir().resolve("backups")
