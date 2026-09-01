package com.athena.reader.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.ui.theme.InscriptionHeader
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.book_continue
import athena.shared.generated.resources.book_highlighted_by
import athena.shared.generated.resources.book_read
import athena.shared.generated.resources.book_sections
import athena.shared.generated.resources.cd_back
import athena.shared.generated.resources.cd_favorite
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    coordinate: Coordinate,
    viewModel: BookDetailViewModel,
    onRead: (Coordinate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(coordinate) { viewModel.load(coordinate) }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.book?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(isPrivate = false) }) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(Res.string.cd_favorite),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = { onRead(coordinate) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        state.progress
                            ?.let { stringResource(Res.string.book_continue, it.percent) }
                            ?: stringResource(Res.string.book_read),
                    )
                }
            }

            state.book?.summary?.let { summary ->
                item { Text(summary, style = MaterialTheme.typography.bodyMedium) }
            }

            state.book?.let { book ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.book_sections, book.sectionCount)) },
                        )
                        book.language?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }

            if (state.communityHighlights.isNotEmpty()) {
                item { InscriptionHeader(stringResource(Res.string.book_highlighted_by)) }
                items(state.communityHighlights, key = { it.id }) { highlight ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("“${highlight.text}”", style = MaterialTheme.typography.bodyMedium)
                            highlight.comment?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
