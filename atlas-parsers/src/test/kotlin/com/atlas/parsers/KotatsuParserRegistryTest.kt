package com.atlas.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl

class KotatsuParserRegistryTest {

    private val registry = KotatsuParserRegistry()

    @Test
    fun `bundle lists every vendored source`() {
        // Vendored HEAD c429dceb: 1,413 sources, 430 of them @Broken.
        assertEquals(1413, ParsersBundleImpl.sources.size)
        assertEquals(1413, registry.allSources.size)
    }

    @Test
    fun `default catalogue hides broken sources`() {
        assertEquals(983, registry.sources.size)
        assertEquals(983, registry.count)
        assertTrue(registry.sources.none { it.isBroken })
        assertTrue(registry.allSources.any { it.isBroken })
    }

    @Test
    fun `source names are unique`() {
        val names = registry.allSources.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `findSource resolves a known source`() {
        val source = registry.findSource("MANGADEX")
        assertNotNull(source)
        assertEquals("MangaDex", source?.title)
        assertNull(registry.findSource("NO_SUCH_SOURCE"))
    }

    @Test
    fun `broken sources are not adapted`() {
        val broken = registry.allSources.first { it.isBroken }
        val context = AtlasMangaLoaderContext()
        assertNull(registry.newAdapter(broken.name, context))
    }

    @Test
    fun `newParser builds a parser without network`() {
        val context = AtlasMangaLoaderContext()
        val parser = registry.newParser(registry.findSource("MANGADEX")!!, context)
        assertEquals("MANGADEX", parser.source.name)
    }
}
