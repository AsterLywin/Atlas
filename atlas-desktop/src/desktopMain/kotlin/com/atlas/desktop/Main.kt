package com.atlas.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.atlas.core.library.Book
import com.atlas.core.library.LibraryStore

/**
 * Atlas desktop entry point (Windows / Linux / macOS). Source v1 opens the
 * library only; the reader, downloads and sync are phase 2.
 */
fun main() = application {
    Window(onCloseRequest = { exitApplication() }, title = "Atlas") {
        MaterialTheme {
            LibraryScreen(sampleLibrary())
        }
    }
}

private fun sampleLibrary(): LibraryStore = LibraryStore().apply {
    addBook(
        Book(
            id = "sample-1",
            title = "Atlas — sample book",
            author = "Atlas",
            sourceId = "local",
        ),
    )
}

@Composable
fun LibraryScreen(library: LibraryStore) {
    val books = remember { library.listBooks() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (books.isEmpty()) {
            Text("No books yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(books) { book ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}
