package com.atlas.parsers

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl
import org.koitharu.kotatsu.parsers.model.ParserMangaSource

/**
 * The catalogue of Kotatsu parser sources available to Atlas.
 *
 * The bundle lists every source, broken ones included; the default
 * [sources] hides the ~430 sources upstream marks `@Broken` — they are
 * dead sites and only confuse the catalogue. [allSources] keeps them for
 * diagnostics. Sources are ADDITIVE to Mihon's extension system: nothing
 * here removes or replaces an extension source.
 *
 * Use [newAdapter] to get a ready [com.atlas.core.source.Source] for a
 * source name, e.g. `"MANGADEX"`.
 */
class KotatsuParserRegistry(
    private val bundle: ParsersBundle = ParsersBundleImpl,
) {

    /** Live (not broken) sources, in bundle order. */
    val sources: List<ParserMangaSource>
        get() = bundle.sources.filterNot { it.isBroken }

    /** Every source of the bundle, broken ones included. */
    val allSources: List<ParserMangaSource>
        get() = bundle.sources

    val count: Int get() = sources.size

    fun findSource(name: String): ParserMangaSource? = bundle.findSource(name)

    fun newParser(source: ParserMangaSource, context: MangaLoaderContext): MangaParser =
        bundle.newParser(source, context)

    /**
     * An adapter for the named source, or null when the name is unknown.
     * Broken sources are not adapted: they are dead sites.
     */
    fun newAdapter(
        sourceName: String,
        context: MangaLoaderContext,
    ): KotatsuSourceAdapter? {
        val source = findSource(sourceName) ?: return null
        if (source.isBroken) return null
        return KotatsuSourceAdapter(newParser(source, context), context)
    }

    /** Adapters for every live source. Builds one parser object each. */
    fun allAdapters(context: MangaLoaderContext): List<KotatsuSourceAdapter> =
        sources.map { KotatsuSourceAdapter(newParser(it, context), context) }
}
