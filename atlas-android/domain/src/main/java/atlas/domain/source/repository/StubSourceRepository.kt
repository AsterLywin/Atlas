package atlas.domain.source.repository

import atlas.domain.source.model.StubSource
import kotlinx.coroutines.flow.Flow

interface StubSourceRepository {
    fun subscribeAll(): Flow<List<StubSource>>

    suspend fun getStubSource(id: Long): StubSource?

    suspend fun upsertStubSource(id: Long, lang: String, name: String)
}
