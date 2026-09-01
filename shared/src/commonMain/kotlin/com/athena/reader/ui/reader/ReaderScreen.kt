package com.athena.reader.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.data.session.ReaderPrefs
import com.athena.reader.data.session.ReaderPrefsStore
import com.athena.reader.data.session.ReaderTurnMode
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightColor
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.ui.theme.LocalIsDarkTheme
import com.athena.reader.ui.theme.LocalReaderTypography
import com.athena.reader.ui.theme.ReaderPapyrusBackdrop
import com.athena.reader.ui.theme.ReaderTypography
import com.athena.reader.ui.theme.manuscriptFamily
import com.athena.reader.ui.theme.surface
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.action_next
import athena.shared.generated.resources.action_previous
import athena.shared.generated.resources.cd_back
import athena.shared.generated.resources.cd_reading_settings
import athena.shared.generated.resources.reader_missing
import athena.shared.generated.resources.reader_page_of
import athena.shared.generated.resources.reader_private_highlight
import athena.shared.generated.resources.reader_public_highlight
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    coordinate: Coordinate,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefsStore = koinInject<ReaderPrefsStore>()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle(ReaderPrefs())
    var selection by remember { mutableStateOf<IntRange?>(null) }
    var selectedText by remember { mutableStateOf("") }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    var turnCommand by remember { mutableStateOf<PageTurnCommand?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coordinate) { viewModel.load(coordinate) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    DisposableEffect(Unit) {
        onDispose { viewModel.onLeaveReader() }
    }

    val garamond = manuscriptFamily()
    val reading = ReaderTypography(
        fontSize = prefs.effectiveFontSize,
        lineHeightMultiplier = prefs.lineHeight,
        fontFamily = prefs.font.toFamily(garamond),
    )

    CompositionLocalProvider(LocalReaderTypography provides reading) {
        ReaderPapyrusBackdrop(modifier) {
            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.PageDown, Key.DirectionRight -> {
                                turnCommand = PageTurnCommand.Next
                                true
                            }
                            Key.PageUp, Key.DirectionLeft -> {
                                turnCommand = PageTurnCommand.Previous
                                true
                            }
                            else -> false
                        }
                    },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = state.currentSection?.title ?: state.book?.title.orEmpty(),
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
                            IconButton(onClick = { showSettings = !showSettings }) {
                                Icon(Icons.Default.Tune, contentDescription = stringResource(Res.string.cd_reading_settings))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                            navigationIconContentColor = MaterialTheme.colorScheme.primary,
                            actionIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                bottomBar = {
                    Column {
                        if (showSettings) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                tonalElevation = 2.dp,
                            ) {
                                ReaderSettingsPanel(
                                    prefs = prefs,
                                    onChange = { next ->
                                        scope.launch { prefsStore.update { next } }
                                    },
                                )
                            }
                        }
                        PageNavigator(
                            pageIndex = pageIndex,
                            pageCount = pageCount.coerceAtLeast(1),
                            onPrevious = { turnCommand = PageTurnCommand.Previous },
                            onNext = { turnCommand = PageTurnCommand.Next },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when {
                        state.isLoading -> LoadingReader()
                        state.currentSection == null -> MissingText()
                        else -> ReadingPages(
                            state = state,
                            prefs = prefs,
                            pageIndex = pageIndex,
                            onPageIndex = { pageIndex = it },
                            onPageCount = { pageCount = it },
                            turnCommand = turnCommand,
                            onTurnCommandConsumed = { turnCommand = null },
                            selection = selection,
                            onSelection = { range, text ->
                                selection = range
                                selectedText = text
                            },
                            onPosition = viewModel::showPosition,
                        )
                    }

                    val activeSelection = selection
                    if (activeSelection != null && state.canHighlight) {
                        HighlighterBar(
                            onHighlight = { color, visibility ->
                                viewModel.highlight(activeSelection, selectedText, color, visibility)
                                selection = null
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingPages(
    state: ReaderUiState,
    prefs: ReaderPrefs,
    pageIndex: Int,
    onPageIndex: (Int) -> Unit,
    onPageCount: (Int) -> Unit,
    turnCommand: PageTurnCommand?,
    onTurnCommandConsumed: () -> Unit,
    selection: IntRange?,
    onSelection: (IntRange?, String) -> Unit,
    onPosition: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalReaderTypography.current.bodyStyle.merge(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val margin = prefs.marginDp.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { (maxWidth - margin * 2).toPx().roundToInt() }
        val heightPx = with(density) { (maxHeight - 24.dp).toPx().roundToInt() }
        val pages = remember(state.sections, widthPx, heightPx, style) {
            paginateBook(state.sections, measurer, style, widthPx, heightPx)
        }
        LaunchedEffect(pages.size) { onPageCount(pages.size) }

        val bookKey = state.book?.coordinate?.asString()
        val measured = widthPx > 16 && heightPx > 16
        var restoredFor by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(bookKey, measured) {
            if (bookKey == null || pages.isEmpty() || !measured) return@LaunchedEffect
            if (restoredFor == bookKey) return@LaunchedEffect
            restoredFor = bookKey
            onPageIndex(pageIndexFor(pages, state.sectionIndex, state.restoreOffset))
        }
        LaunchedEffect(pages.size) {
            if (pages.isNotEmpty() && pageIndex > pages.lastIndex) onPageIndex(pages.lastIndex)
        }

        LaunchedEffect(pageIndex, pages) {
            val page = pages.getOrNull(pageIndex) ?: return@LaunchedEffect
            onPosition(page.sectionIndex, page.start)
            onSelection(null, "")
        }

        if (pages.isEmpty()) {
            MissingText()
            return@BoxWithConstraints
        }

        val safeIndex = pageIndex.coerceIn(0, pages.lastIndex)
        when (prefs.turnMode) {
            ReaderTurnMode.Paged -> PageTurnSurface(
                pageIndex = safeIndex,
                pageCount = pages.size,
                command = turnCommand,
                onCommandConsumed = onTurnCommandConsumed,
                onPageChange = onPageIndex,
                enabled = selection == null,
            ) { index ->
                PageBody(
                    state = state,
                    page = pages[index.coerceIn(0, pages.lastIndex)],
                    margin = prefs.marginDp,
                    onSelection = onSelection,
                )
            }
            ReaderTurnMode.Scroll -> ScrollPages(
                pages = pages,
                pageIndex = safeIndex,
                command = turnCommand,
                onCommandConsumed = onTurnCommandConsumed,
                onPageChange = onPageIndex,
            ) { page ->
                PageBody(
                    state = state,
                    page = page,
                    margin = prefs.marginDp,
                    onSelection = onSelection,
                )
            }
        }
    }
}

@Composable
private fun PageBody(
    state: ReaderUiState,
    page: ReaderPage,
    margin: Int,
    onSelection: (IntRange?, String) -> Unit,
) {
    val section = state.sections.getOrNull(page.sectionIndex)
    val slice = section?.content?.substring(page.start, page.end.coerceAtMost(section.content.length)).orEmpty()
    val marks = if (section == null) {
        emptyList()
    } else {
        highlightsOnPage(
            highlights = state.highlights,
            page = page,
            section = section.coordinate,
            singleSection = state.sections.size <= 1,
        )
    }
    HighlightableText(
        content = slice,
        markup = state.markup,
        highlights = marks,
        horizontalPadding = margin.dp,
        onSelectionChange = { range, text ->
            if (range == null) {
                onSelection(null, "")
            } else {
                val shifted = IntRange(range.first + page.start, range.last + page.start)
                onSelection(shifted, text)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollPages(
    pages: List<ReaderPage>,
    pageIndex: Int,
    command: PageTurnCommand?,
    onCommandConsumed: () -> Unit,
    onPageChange: (Int) -> Unit,
    page: @Composable (ReaderPage) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pageIndex)
    val snap = rememberSnapFlingBehavior(listState)
    val pageIndexState = rememberUpdatedState(pageIndex)
    val lastIndexState = rememberUpdatedState(pages.lastIndex)
    val onPageChangeState = rememberUpdatedState(onPageChange)
    val onConsumedState = rememberUpdatedState(onCommandConsumed)
    val commandState = rememberUpdatedState(command)

    LaunchedEffect(Unit) {
        snapshotFlow { commandState.value }
            .filterNotNull()
            .collect { request ->
                onConsumedState.value()
                val index = pageIndexState.value
                val last = lastIndexState.value
                val target = when (request) {
                    PageTurnCommand.Next -> (index + 1).coerceAtMost(last)
                    PageTurnCommand.Previous -> (index - 1).coerceAtLeast(0)
                }
                if (target != index) {
                    listState.animateScrollToItem(target)
                    onPageChangeState.value(target)
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> onPageChangeState.value(index) }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snap,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(pages, key = { index, item -> "${item.sectionIndex}:${item.start}:$index" }) { _, item ->
            Box(Modifier.fillParentMaxHeight().fillMaxWidth()) { page(item) }
        }
    }
}

private fun highlightsOnPage(
    highlights: List<Highlight>,
    page: ReaderPage,
    section: Coordinate,
    singleSection: Boolean,
): List<Highlight> {
    val pageLength = page.end - page.start
    return highlights.filter { it.belongsTo(section, singleSection) && it.hasRange }.mapNotNull { mark ->
        val start = mark.startOffset - page.start
        val end = mark.endOffset - page.start
        if (end <= 0 || start >= pageLength) null
        else mark.copy(
            startOffset = start.coerceAtLeast(0),
            endOffset = end.coerceAtMost(pageLength),
        )
    }
}

@Composable
private fun HighlighterBar(
    onHighlight: (HighlightColor, HighlightVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalIsDarkTheme.current
    var visibility by remember { mutableStateOf(HighlightVisibility.Public) }

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightColor.entries.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color.surface(darkTheme))
                            .clickable { onHighlight(color, visibility) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            VisibilityToggle(visibility) { visibility = it }
        }
    }
}

@Composable
private fun VisibilityToggle(
    visibility: HighlightVisibility,
    onChange: (HighlightVisibility) -> Unit,
) {
    val isPublic = visibility == HighlightVisibility.Public
    TextButton(
        onClick = {
            onChange(if (isPublic) HighlightVisibility.Private else HighlightVisibility.Public)
        },
    ) {
        Icon(
            imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(
                if (isPublic) Res.string.reader_public_highlight else Res.string.reader_private_highlight,
            ),
        )
    }
}

@Composable
private fun PageNavigator(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Column {
            LinearProgressIndicator(
                progress = { if (pageCount <= 0) 0f else (pageIndex + 1f) / pageCount },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                QuietPageButton(
                    label = stringResource(Res.string.action_previous),
                    enabled = pageIndex > 0,
                    onClick = onPrevious,
                )
                Text(
                    text = stringResource(Res.string.reader_page_of, pageIndex + 1, pageCount),
                    style = MaterialTheme.typography.labelMedium,
                )
                QuietPageButton(
                    label = stringResource(Res.string.action_next),
                    enabled = pageIndex < pageCount - 1,
                    onClick = onNext,
                )
            }
        }
    }
}

@Composable
private fun QuietPageButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun LoadingReader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MissingText() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.reader_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
