package com.atlas.desktop

import com.atlas.ui.FileSettingsStore
import com.atlas.ui.model.AppLanguage
import com.atlas.ui.model.AppSettings
import com.atlas.ui.model.AppTheme
import com.atlas.ui.model.ReaderMode
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The desktop settings file: defaults, round-trip, corrupt file. */
class FileSettingsStoreTest {

    private lateinit var tempDir: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "atlas-settings-test-${System.nanoTime()}")
            .also { it.mkdirs() }
        settingsFile = tempDir.resolve("atlas-settings.properties")
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `a missing file loads the defaults`() {
        assertEquals(AppSettings(), FileSettingsStore(settingsFile).load())
    }

    @Test
    fun `settings survive a save and reload`() {
        val store = FileSettingsStore(settingsFile)
        val saved = AppSettings(
            language = AppLanguage.Fa,
            theme = AppTheme.Dark,
            readerMode = ReaderMode.Webtoon,
        )
        store.save(saved)
        assertEquals(saved, FileSettingsStore(settingsFile).load())
    }

    @Test
    fun `an unknown value falls back to its default`() {
        settingsFile.writeText("language=xx\ntheme=dark\nreader_mode=pager\n")
        assertEquals(
            AppSettings(language = AppLanguage.System, theme = AppTheme.Dark),
            FileSettingsStore(settingsFile).load(),
        )
    }

    @Test
    fun `each setting round-trips on its own`() {
        val store = FileSettingsStore(settingsFile)
        store.save(AppSettings(language = AppLanguage.En))
        assertEquals(AppLanguage.En, FileSettingsStore(settingsFile).load().language)
        store.save(AppSettings(theme = AppTheme.Light))
        assertEquals(AppTheme.Light, FileSettingsStore(settingsFile).load().theme)
        store.save(AppSettings(readerMode = ReaderMode.Webtoon))
        assertEquals(ReaderMode.Webtoon, FileSettingsStore(settingsFile).load().readerMode)
    }
}
