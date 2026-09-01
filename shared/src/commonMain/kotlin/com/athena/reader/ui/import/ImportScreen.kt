package com.athena.reader.ui.importer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.data.importer.ImportedBook
import com.athena.reader.data.importer.PublishVisibility
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.ui.theme.InscriptionHeader
import com.athena.reader.ui.theme.TempleSymbol
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.import_title
import org.jetbrains.compose.resources.stringResource

/**
 * The gateway screen: drop a file, fix its metadata, decide who may read it.
 *
 * [onPickFile] is supplied by each platform, because choosing a file is the one
 * part that cannot be shared — Android goes through the storage access
 * framework, the desktop through a native dialog and drag-and-drop.
 */
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onPickFile: () -> Unit,
    onRead: (Coordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.published?.let { coordinate ->
            item {
                PublishedCard(
                    sectionLabel = coordinate.identifier,
                    onAnother = viewModel::reset,
                    onRead = { onRead(coordinate) },
                )
            }
            return@LazyColumn
        }

        item { InscriptionHeader(stringResource(Res.string.import_title)) }
        item { DropTarget(isParsing = state.isParsing, hasBook = state.hasBook, onPickFile = onPickFile) }

        state.error?.let { message ->
            item { ErrorRow(message = message, onDismiss = viewModel::dismissError) }
        }

        val book = state.book ?: return@LazyColumn

        item { MetadataForm(book = book, viewModel = viewModel) }
        item { VisibilityChoice(selected = state.visibility, onSelect = viewModel::setVisibility) }
        item { ChapterSummary(book) }

        if (state.canRead) {
            item {
                OutlinedButton(
                    onClick = { viewModel.openReader(onRead) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Read") }
            }
        }

        item {
            Button(
                onClick = viewModel::publish,
                enabled = state.canPublish && !state.isPublishing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isPublishing) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (state.publishingTotal > 0) {
                            "Signing ${state.publishingDone.coerceAtLeast(1)} / ${state.publishingTotal}"
                        } else {
                            "Signing…"
                        },
                    )
                } else {
                    Text(
                        when (state.visibility) {
                            PublishVisibility.Public -> "Publish to the library"
                            PublishVisibility.Private -> "Add to my private shelf"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropTarget(isParsing: Boolean, hasBook: Boolean, onPickFile: () -> Unit) {
    OutlinedCard(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPickFile),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(28.dp),
        ) {
            if (isParsing) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Reading the file…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            Icon(
                imageVector = TempleSymbol,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasBook) "Drop another file, or click to choose" else "Drop a book here",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "PDF, EPUB or plain text — published in the clear on the relays",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The step that makes the difference between a library and a pile of files.
 * Importers guess a title from the first heading and a file name; the guess is
 * often wrong, and this is where it gets corrected before anyone else sees it.
 */
@Composable
private fun MetadataForm(book: ImportedBook, viewModel: ImportViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Details", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = book.title,
                onValueChange = viewModel::editTitle,
                label = { Text("Title") },
                isError = book.title.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = book.author,
                onValueChange = viewModel::editAuthor,
                label = { Text("Author") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = book.summary,
                onValueChange = viewModel::editSummary,
                label = { Text("Summary") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = book.language,
                    onValueChange = viewModel::editLanguage,
                    label = { Text("Language") },
                    placeholder = { Text("it") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = book.topics.joinToString(", "),
                    onValueChange = viewModel::editTopics,
                    label = { Text("Topics") },
                    placeholder = { Text("classics, philosophy") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

/**
 * Public versus private is not a privacy toggle here — it is a legal one, and
 * saying so plainly is more useful than a lock icon. Most books a person owns
 * cannot lawfully be republished.
 */
@Composable
private fun VisibilityChoice(
    selected: PublishVisibility,
    onSelect: (PublishVisibility) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Who can read it", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            VisibilityOption(
                selected = selected == PublishVisibility.Public,
                icon = Icons.Default.Public,
                title = "Public",
                detail = "Anyone on nostr, in any client. For public-domain works, " +
                    "or writing that is yours to share.",
                onClick = { onSelect(PublishVisibility.Public) },
            )
            VisibilityOption(
                selected = selected == PublishVisibility.Private,
                icon = Icons.Default.Lock,
                title = "Private",
                detail = "Encrypted to your key at a blinded address: your other " +
                    "devices, and nobody else. For books you own but may not republish.",
                onClick = { onSelect(PublishVisibility.Private) },
            )
        }
    }
}

@Composable
private fun VisibilityOption(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp),
        )
    }
}

@Composable
private fun ChapterSummary(book: ImportedBook) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${book.sections.size} chapters · ${book.wordCount} words",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            book.sections.take(5).forEach { section ->
                Text(
                    text = "• ${section.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (book.sections.size > 5) {
                Text(
                    text = "…and ${book.sections.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PublishedCard(sectionLabel: String, onAnother: () -> Unit, onRead: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Published", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "It is on the relays and already in your library.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sectionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRead, modifier = Modifier.fillMaxWidth()) { Text("Read") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Add another book")
            }
        }
    }
}

@Composable
private fun ErrorRow(message: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss")
        }
    }
}
