package com.athena.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.ui.theme.InscriptionHeader
import com.athena.reader.ui.theme.TempleSymbol

@Composable
fun ReaderHubScreen(
    viewModel: ReaderHubViewModel,
    onOpen: (Coordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            ) {
                Icon(
                    imageVector = TempleSymbol,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "READER",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val last = state.lastBook
        if (last != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    InscriptionHeader("Continue")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onOpen(last.coordinate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open ${last.title}") }
                }
            }
        } else if (state.continueReading.isEmpty()) {
            item {
                Text(
                    text = "Open a book from the Library, or add a file, to start reading. " +
                        "The page-turn, type size and fonts below apply the next time a book is open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (state.continueReading.isNotEmpty()) {
            item {
                InscriptionHeader("Recent", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(state.continueReading, key = { it.first.coordinate.asString() }) { (book, progress) ->
                ContinueCard(book, progress) { onOpen(book.coordinate) }
            }
        }

        item {
            InscriptionHeader("Reading", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        item {
            ReaderSettingsPanel(
                prefs = state.prefs,
                onChange = viewModel::updatePrefs,
            )
        }
    }
}

@Composable
private fun ContinueCard(book: Book, progress: ReadingProgress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("${progress.percent}%", style = MaterialTheme.typography.labelSmall)
        }
    }
}
