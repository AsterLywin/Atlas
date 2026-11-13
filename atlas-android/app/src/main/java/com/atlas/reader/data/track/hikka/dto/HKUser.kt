package com.atlas.reader.data.track.hikka.dto

import kotlinx.serialization.Serializable

@Serializable
data class HKUser(
    val reference: String,
    val username: String,
)
