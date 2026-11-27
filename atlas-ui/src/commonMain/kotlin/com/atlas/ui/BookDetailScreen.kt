package com.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiChapter
import com.atlas.ui.state.DownloadManager
import com.atlas.ui.theme.LocalAtlasStrings

/**
 * A book's chapters: read one, add the book to the library first.
 * Chapters are loaded by the caller (library first, source
 * otherwise) and passed in; null means still loading.
 */
@Composable
fun BookDetailScreen(
    book: UiBook,
    inLibrary: Boolean,
    chapters: List<UiChapter>?,
    loadError: Boolean,
    downloadManager: DownloadManager,
    onReloadChapters: () -> Unit,
    onAddToLibrary: (List<UiChapter>) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onOpenChapter: (UiChapter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(strings.back) }
            Spacer(Modifier.width(12.dp))
            Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(12.dp))
        if (inLibrary) {
            OutlinedButton(onClick = onRemoveFromLibrary) {
                Text(strings.removeFromLibrary)
            }
        } else {
            Button(
                onClick = { (chapters ?: emptyList()).let(onAddToLibrary) },
                enabled = chapters != null,
            ) {
                Text(strings.addToLibrary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            strings.chaptersCount(chapters?.size ?: 0),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        when {
            chapters == null -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            loadError -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.loadError)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReloadChapters) { Text(strings.retry) }
                }
            }
            chapters.isEmpty() -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(strings.noChapters)
            }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(chapters, key = { it.id }) { chapter ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChapter(chapter) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = chapter.title.ifBlank {
                                strings.chapterTitle(formatChapterNumber(chapter.number))
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (chapter.read || chapter.progress >= 1f) {
                            Text(
                                strings.markAsRead,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        ChapterDownloadControl(
                            book = book,
                            chapter = chapter,
                            downloadManager = downloadManager,
                        )
                    }
                }
            }
        }
    }
}

/** "1.5" stays "1.5", "2.0" prints as "2". */
fun formatChapterNumber(number: Float): String =
    if (number == number.toInt().toFloat()) number.toInt().toString() else number.toString()
