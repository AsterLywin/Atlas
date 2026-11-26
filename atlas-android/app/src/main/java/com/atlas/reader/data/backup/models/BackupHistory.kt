package com.atlas.reader.data.backup.models

import atlas.core.common.extensions.toInstantOrNull
import atlas.domain.history.model.History
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
) {
    fun getHistoryImpl(): History {
        return History.create().copy(
            readAt = lastRead.toInstantOrNull(),
            readDuration = readDuration,
        )
    }
}
