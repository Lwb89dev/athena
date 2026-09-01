package com.athena.reader.ui.highlights

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.ui.theme.InscriptionHeader
import com.athena.reader.ui.theme.LocalIsDarkTheme
import com.athena.reader.ui.theme.TempleSymbol
import com.athena.reader.ui.theme.surface
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.cd_delete
import athena.shared.generated.resources.cd_make_private
import athena.shared.generated.resources.cd_make_public
import athena.shared.generated.resources.filter_all
import athena.shared.generated.resources.filter_private
import athena.shared.generated.resources.filter_public
import athena.shared.generated.resources.highlights_empty_in
import athena.shared.generated.resources.highlights_empty_out
import athena.shared.generated.resources.highlights_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun HighlightsScreen(
    viewModel: HighlightsViewModel,
    onOpenBook: (Coordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        InscriptionHeader(stringResource(Res.string.highlights_title), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        FilterRow(state.filter, viewModel::setFilter)

        if (state.highlights.isEmpty()) {
            EmptyHighlights(isLoggedIn = state.isLoggedIn)
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.highlights, key = { it.id }) { highlight ->
                HighlightCard(
                    highlight = highlight,
                    onOpen = { onOpenBook(highlight.bookCoordinate) },
                    onToggleVisibility = { viewModel.toggleVisibility(highlight) },
                    onDelete = { viewModel.delete(highlight) },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(active: HighlightFilter, onSelect: (HighlightFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        HighlightFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == active,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                HighlightFilter.All -> Res.string.filter_all
                                HighlightFilter.Public -> Res.string.filter_public
                                HighlightFilter.Private -> Res.string.filter_private
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun HighlightCard(
    highlight: Highlight,
    onOpen: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit,
) {
    val darkTheme = LocalIsDarkTheme.current
    val isPublic = highlight.visibility == HighlightVisibility.Public

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            // A colour stripe rather than a coloured card: the marker colour is
            // information, not decoration, and must survive a dark background.
            Box(
                Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(highlight.color.surface(darkTheme)),
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(highlight.text, style = MaterialTheme.typography.bodyMedium)
                highlight.comment?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock,
                    contentDescription = stringResource(
                        if (isPublic) Res.string.cd_make_private else Res.string.cd_make_public,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.cd_delete), Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyHighlights(isLoggedIn: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = TempleSymbol,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (isLoggedIn) Res.string.highlights_empty_in else Res.string.highlights_empty_out,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
