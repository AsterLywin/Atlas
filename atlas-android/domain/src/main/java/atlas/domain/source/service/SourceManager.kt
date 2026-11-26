package atlas.domain.source.service

import atlas.domain.source.model.StubSource
import com.atlas.reader.source.Source
import com.atlas.reader.source.online.HttpSource
import kotlinx.coroutines.flow.Flow

interface SourceManager {

    val sources: Flow<List<Source>>

    suspend fun get(sourceKey: Long): Source?

    suspend fun getOrStub(sourceKey: Long): Source

    suspend fun getAll(): List<Source>

    suspend fun getOnlineSources(): List<HttpSource>

    suspend fun getStubSources(): List<StubSource>
}
