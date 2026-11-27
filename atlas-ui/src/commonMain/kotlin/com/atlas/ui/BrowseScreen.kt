package com.atlas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.atlas.ui.data.AtlasRepository
import com.atlas.ui.model.UiBook
import com.atlas.ui.model.UiSource
import com.atlas.ui.theme.LocalAtlasStrings
import kotlinx.coroutines.launch

/** Browse: pick a source, list or search its catalogue, open a book. */
@Composable
fun BrowseScreen(
    repository: AtlasRepository,
    imageLoaderForBook: (UiBook) -> ImageLoader,
    onOpenBook: (UiBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAtlasStrings.current
    val sources = remember { repository.listSources() }
    var selectedSource by remember { mutableStateOf<UiSource?>(sources.firstOrNull()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UiBook>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(search: String?) {
        val source = selectedSource ?: return
        loading = true
        error = false
        scope.launch {
            val books = try {
                if (search.isNullOrBlank()) {
                    repository.browseBooks(source.id)
                } else {
                    repository.searchBooks(source.id, search)
                }
            } catch (_: Exception) {
                error = true
                emptyList()
            }
            results = books
            loading = false
        }
    }

    LaunchedEffect(selectedSource) { load(null) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.source, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Text(selectedSource?.title ?: "")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    sources.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.title) },
                            onClick = {
                                selectedSource = source
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(strings.searchHint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { load(query) }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { load(query) }) {
                Text(strings.search)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.loadError)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { load(query.ifBlank { null }) }) {
                        Text(strings.retry)
                    }
                }
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(strings.browseEmpty)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        imageLoader = imageLoaderForBook(book),
                        chapterCount = null,
                        onClick = { onOpenBook(book) },
                    )
                }
            }
        }
    }
}
