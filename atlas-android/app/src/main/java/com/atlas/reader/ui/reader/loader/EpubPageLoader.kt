package com.atlas.reader.ui.reader.loader

import com.atlas.reader.source.model.Page
import com.atlas.reader.ui.reader.model.ReaderPage
import atlas.core.archive.EpubReader

/**
 * Loader used to load a chapter from a .epub file.
 */
internal class EpubPageLoader(private val reader: EpubReader) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return reader.getImagesFromPages().mapIndexed { i, path ->
            ReaderPage(i).apply {
                stream = { reader.getInputStream(path)!! }
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }
}
