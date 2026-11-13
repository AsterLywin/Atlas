package atlas.domain.source.model

import com.atlas.reader.source.Source
import com.atlas.reader.source.model.FilterList
import com.atlas.reader.source.model.MangasPage
import com.atlas.reader.source.model.Page
import com.atlas.reader.source.model.SChapter
import com.atlas.reader.source.model.SManga
import com.atlas.reader.source.model.SMangaUpdate

data class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage = throw SourceNotInstalledException()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw SourceNotInstalledException()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw SourceNotInstalledException()

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw SourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: Source): StubSource {
            return StubSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()
