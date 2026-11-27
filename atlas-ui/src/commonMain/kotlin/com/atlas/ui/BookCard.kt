package com.atlas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.atlas.ui.model.UiBook
import com.atlas.ui.theme.LocalAtlasStrings

/** A book cover card, shared by the library and browse grids. */
@Composable
fun BookCard(
    book: UiBook,
    imageLoader: ImageLoader,
    chapterCount: Int?,
    inLibrary: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        ),
    ) {
        Column {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = strings.coverDescription(book.title),
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                if (chapterCount != null) {
                    Text(
                        strings.chaptersCount(chapterCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (inLibrary) {
                    Text(
                        strings.inLibrary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
