package com.atlas.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.state.UiDownloadState
import com.atlas.ui.theme.LocalAtlasStrings

/**
 * The download button + progress for one chapter, shared by the book
 * detail list and the reader header: download, queued, per-page
 * progress with cancel, downloaded, and retry after a failure.
 */
@Composable
fun ChapterDownloadControl(
    book: UiBook,
    chapter: UiChapter,
    downloadManager: DownloadManager,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    val state by downloadManager.state(book, chapter).collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (val current = state) {
            UiDownloadState.NotDownloaded -> TextButton(
                onClick = { downloadManager.download(book, chapter) },
            ) { Text(strings.download) }
            UiDownloadState.Queued -> Text(
                strings.downloadQueued,
                style = MaterialTheme.typography.bodySmall,
            )
            is UiDownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { current.progress },
                    modifier = Modifier.width(72.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    strings.downloadPercent((current.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { downloadManager.cancel(book, chapter) }) {
                    Text(strings.downloadCancel)
                }
            }
            UiDownloadState.Downloaded -> Text(
                strings.downloaded,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            is UiDownloadState.Failed -> TextButton(
                onClick = { downloadManager.download(book, chapter) },
            ) { Text(strings.retry) }
        }
    }
}
