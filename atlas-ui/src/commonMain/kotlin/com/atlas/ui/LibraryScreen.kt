package com.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.theme.LocalAtlasStrings

/** The library grid: books persisted by the platform store. */
@Composable
fun LibraryScreen(
    repository: AtlasRepository,
    imageLoaderForBook: (UiBook) -> ImageLoader,
    version: Int,
    onOpenBook: (UiBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    var books by remember(version) { mutableStateOf<List<UiBook>?>(null) }
    var chapterCounts by remember(version) { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(version) {
        val loaded = repository.listLibraryBooks()
        books = loaded
        chapterCounts = loaded.associate { it.id to repository.listLibraryChapters(it.id).size }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.tabLibrary, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        when (val current = books) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.loading)
            }
            else -> if (current.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.libraryEmpty)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            imageLoader = imageLoaderForBook(book),
                            chapterCount = chapterCounts[book.id],
                            onClick = { onOpenBook(book) },
                        )
                    }
                }
            }
        }
    }
}
