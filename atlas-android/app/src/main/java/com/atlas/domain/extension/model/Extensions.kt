package com.atlas.domain.extension.model

import com.atlas.reader.extension.model.Extension

data class Extensions(
    val updates: List<Extension.Installed>,
    val loaded: List<Extension.Loaded>,
    val available: List<Extension.Available>,
    val notLoaded: List<Extension.NotLoaded>,
)
