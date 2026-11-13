package com.atlas.presentation.track.components

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.atlas.reader.data.track.Tracker
import com.atlas.test.DummyTracker

internal class TrackLogoIconPreviewProvider : PreviewParameterProvider<Tracker> {

    override val values: Sequence<Tracker>
        get() = sequenceOf(
            DummyTracker(
                id = 1L,
                name = "Dummy Tracker",
            ),
        )
}
