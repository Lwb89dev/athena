package com.athena.reader.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.data.repository.PeopleList
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.ui.theme.GreekKey
import com.athena.reader.ui.theme.InscriptionHeader
import com.athena.reader.ui.theme.TempleSymbol
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.asking_relays
import athena.shared.generated.resources.articles
import athena.shared.generated.resources.books
import athena.shared.generated.resources.browse_global
import athena.shared.generated.resources.clear_history
import athena.shared.generated.resources.empty_acquaintances
import athena.shared.generated.resources.empty_acquaintances_anon
import athena.shared.generated.resources.empty_following_anon
import athena.shared.generated.resources.empty_following_nobody
import athena.shared.generated.resources.empty_global
import athena.shared.generated.resources.empty_list
import athena.shared.generated.resources.empty_on_device
import athena.shared.generated.resources.feed_acquaintances
import athena.shared.generated.resources.feed_acquaintances_detail
import athena.shared.generated.resources.feed_following
import athena.shared.generated.resources.feed_following_detail
import athena.shared.generated.resources.feed_global
import athena.shared.generated.resources.feed_global_detail
import athena.shared.generated.resources.feed_on_device
import athena.shared.generated.resources.feed_on_device_detail
import athena.shared.generated.resources.feed_showing
import athena.shared.generated.resources.latest
import athena.shared.generated.resources.remove_from_history
import athena.shared.generated.resources.results
import athena.shared.generated.resources.search_hint
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenBook: (Coordinate) -> Unit,
    onResume: (Coordinate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { LibraryMasthead() }

        item {
            ScopeSelector(
                scope = state.scope,
                lists = state.availableLists,
                onSelect = viewModel::setScope,
            )
        }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (state.continueArticles.isNotEmpty()) {
            item {
                HistoryHeader(
                    title = stringResource(Res.string.articles),
                    onClear = viewModel::clearArticleHistory,
                )
            }
            item {
                ContinueReadingRow(
                    entries = state.continueArticles,
                    onResume = onResume,
                    onDismiss = viewModel::dismissHistory,
                )
            }
        }

        if (state.continueBooks.isNotEmpty()) {
            item {
                HistoryHeader(
                    title = stringResource(Res.string.books),
                    onClear = viewModel::clearBookHistory,
                )
            }
            item {
                ContinueReadingRow(
                    entries = state.continueBooks,
                    onResume = onResume,
                    onDismiss = viewModel::dismissHistory,
                )
            }
        }

        item {
            InscriptionHeader(
                if (state.query.isBlank()) stringResource(Res.string.latest) else stringResource(Res.string.results),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (state.books.isEmpty()) {
            item {
                EmptyLibrary(
                    isRefreshing = state.isRefreshing,
                    scope = state.scope,
                    followsNobody = state.followsNobody,
                    acquaintancesEmpty = state.acquaintancesEmpty,
                    isLoggedIn = state.isLoggedIn,
                    onGoGlobal = { viewModel.setScope(FeedScope.Global) },
                )
            }
        }

        items(state.books, key = { it.coordinate.asString() }) { book ->
            BookRow(
                book = book,
                authorName = if (book.imported) {
                    book.authorName
                } else {
                    state.authors[book.authorPubkey]?.displayName ?: book.authorName
                },
                onClick = { onOpenBook(book.coordinate) },
            )
        }
    }
}

/**
 * The feed switch.
 *
 * Prominent on purpose: which slice of nostr you are looking at changes what
 * the app *is*, and a user landing on a spam-filled global feed with no visible
 * way out would reasonably conclude the app is broken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSelector(
    scope: FeedScope,
    lists: List<PeopleList>,
    onSelect: (FeedScope) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = scopeTitle(scope),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.feed_showing)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScopeMenuItem(
                title = stringResource(Res.string.feed_following),
                detail = stringResource(Res.string.feed_following_detail),
                onClick = {
                    onSelect(FeedScope.Following)
                    expanded = false
                },
            )
            ScopeMenuItem(
                title = stringResource(Res.string.feed_acquaintances),
                detail = stringResource(Res.string.feed_acquaintances_detail),
                onClick = {
                    onSelect(FeedScope.Acquaintances)
                    expanded = false
                },
            )
            ScopeMenuItem(
                title = stringResource(Res.string.feed_on_device),
                detail = stringResource(Res.string.feed_on_device_detail),
                onClick = {
                    onSelect(FeedScope.OnDevice)
                    expanded = false
                },
            )
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text("${list.title}  ·  ${list.pubkeys.size}") },
                    onClick = {
                        onSelect(FeedScope.Curated(list))
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            ScopeMenuItem(
                title = stringResource(Res.string.feed_global),
                detail = stringResource(Res.string.feed_global_detail),
                onClick = {
                    onSelect(FeedScope.Global)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun scopeTitle(scope: FeedScope): String = when (scope) {
    FeedScope.Following -> stringResource(Res.string.feed_following)
    FeedScope.Acquaintances -> stringResource(Res.string.feed_acquaintances)
    FeedScope.OnDevice -> stringResource(Res.string.feed_on_device)
    FeedScope.Global -> stringResource(Res.string.feed_global)
    is FeedScope.Curated -> scope.list.title
}

@Composable
private fun ScopeMenuItem(title: String, detail: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun LibraryMasthead() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = TempleSymbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "ATHENA",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        GreekKey(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 24.dp))
    }
}

@Composable
private fun HistoryHeader(title: String, onClear: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(Res.string.clear_history)) }
        }
        Spacer(Modifier.height(6.dp))
        GreekKey(Modifier.fillMaxWidth().height(8.dp))
    }
}

@Composable
private fun ContinueReadingRow(
    entries: List<Pair<Book, ReadingProgress>>,
    onResume: (Coordinate) -> Unit,
    onDismiss: (Coordinate) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.first.coordinate.asString() }) { (book, progress) ->
            Box(Modifier.width(200.dp)) {
                Card(
                    onClick = { onResume(book.coordinate) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp).padding(end = 20.dp)) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("${progress.percent}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(
                    onClick = { onDismiss(book.coordinate) },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.remove_from_history),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, authorName: String?, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp)) {
            CoverPlaceholder(book.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                authorName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                book.summary?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Covers are optional on nostr, and most publications have none. Rather than a
 * grey box, fall back to the first letter on a tinted card — it still gives the
 * eye something to scan a list by.
 */
@Composable
private fun CoverPlaceholder(title: String) {
    val tablet = RoundedCornerShape(2.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 48.dp, height = 68.dp)
            .clip(tablet)
            .background(MaterialTheme.colorScheme.primary)
            .padding(3.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, tablet),
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * An empty library has three quite different causes, and telling them apart is
 * the difference between "the app is broken" and "here is what to do next".
 */
@Composable
private fun EmptyLibrary(
    isRefreshing: Boolean,
    scope: FeedScope,
    followsNobody: Boolean,
    acquaintancesEmpty: Boolean,
    isLoggedIn: Boolean,
    onGoGlobal: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.asking_relays), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Icon(
            imageVector = TempleSymbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))

        val message = when {
            scope == FeedScope.OnDevice -> stringResource(Res.string.empty_on_device)
            scope == FeedScope.Following && !isLoggedIn ->
                stringResource(Res.string.empty_following_anon)
            scope == FeedScope.Acquaintances && !isLoggedIn ->
                stringResource(Res.string.empty_acquaintances_anon)
            followsNobody && isLoggedIn -> stringResource(Res.string.empty_following_nobody)
            acquaintancesEmpty && isLoggedIn -> stringResource(Res.string.empty_acquaintances)
            scope == FeedScope.Global -> stringResource(Res.string.empty_global)
            else -> stringResource(Res.string.empty_list)
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (scope != FeedScope.Global) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onGoGlobal) { Text(stringResource(Res.string.browse_global)) }
        }
    }
}
