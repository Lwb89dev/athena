package com.athena.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.platform.DroppedFiles
import com.athena.reader.platform.FilePickers
import kotlinx.coroutines.launch
import com.athena.reader.ui.book.BookDetailScreen
import com.athena.reader.ui.book.BookDetailViewModel
import com.athena.reader.ui.highlights.HighlightsScreen
import com.athena.reader.ui.highlights.HighlightsViewModel
import com.athena.reader.ui.importer.ImportScreen
import com.athena.reader.ui.importer.ImportViewModel
import com.athena.reader.data.session.ReaderPrefs
import com.athena.reader.data.session.ReaderPrefsStore
import com.athena.reader.data.session.SessionStore
import com.athena.reader.ui.library.LibraryScreen
import com.athena.reader.ui.library.LibraryViewModel
import com.athena.reader.ui.navigation.Destinations
import com.athena.reader.ui.onboarding.OnboardingScreen
import com.athena.reader.ui.onboarding.OnboardingViewModel
import com.athena.reader.ui.reader.ReaderHubScreen
import com.athena.reader.ui.reader.ReaderHubViewModel
import com.athena.reader.ui.reader.ReaderScreen
import com.athena.reader.ui.reader.ReaderViewModel
import com.athena.reader.ui.settings.SettingsScreen
import com.athena.reader.ui.settings.SettingsViewModel
import com.athena.reader.ui.theme.AthenaTheme
import com.athena.reader.ui.theme.GreekKey
import com.athena.reader.ui.theme.PapyrusBackdrop
import com.athena.reader.ui.theme.TempleSymbol
import com.athena.reader.ui.theme.athenaNavItemColors
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.nav_add
import athena.shared.generated.resources.nav_highlights
import athena.shared.generated.resources.nav_library
import athena.shared.generated.resources.nav_read
import athena.shared.generated.resources.nav_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class TopLevel(val route: String, val labelRes: StringResource, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevel(Destinations.LIBRARY, Res.string.nav_library, Icons.AutoMirrored.Filled.MenuBook),
    TopLevel(Destinations.READ, Res.string.nav_read, Icons.Default.AutoStories),
    TopLevel(Destinations.HIGHLIGHTS, Res.string.nav_highlights, Icons.Default.Highlight),
    TopLevel(Destinations.IMPORT, Res.string.nav_add, Icons.Default.UploadFile),
    TopLevel(Destinations.SETTINGS, Res.string.nav_settings, Icons.Default.Settings),
)

/**
 * The whole app, shared by the Android and desktop entry points. Each platform
 * only supplies a window and a Koin graph.
 *
 * It deliberately takes no arguments: the nav controller is an implementation
 * detail, and exposing it would force every consumer module to depend on
 * navigation-compose just to call this function.
 */
@Composable
fun AthenaApp(
    openBook: Coordinate? = null,
    modifier: Modifier = Modifier,
) {
    val sessionStore = koinInject<SessionStore>()
    var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(sessionStore) {
        sessionStore.onboardingComplete.collect { onboardingDone = it }
    }

    when (onboardingDone) {
        null -> BootSplash(modifier)
        false -> AthenaTheme {
            PapyrusBackdrop {
                OnboardingScreen(
                    viewModel = koinViewModel<OnboardingViewModel>(),
                    modifier = modifier,
                )
            }
        }
        true -> ReadyApp(openBook = openBook, modifier = modifier)
    }
}

@Composable
private fun BootSplash(modifier: Modifier = Modifier) {
    AthenaTheme {
        PapyrusBackdrop {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TempleSymbol,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

@Composable
private fun ReadyApp(openBook: Coordinate?, modifier: Modifier) {
    val navController = rememberNavController()

    // A `nostr:naddr…` link opens the library and then pushes the book, so Back
    // lands somewhere sensible instead of closing the app.
    LaunchedEffect(openBook) {
        if (openBook != null) navController.navigate(Destinations.book(openBook))
    }

    // A file dropped anywhere in the window belongs to the import screen, so
    // navigate there rather than making the user find it themselves.
    LaunchedEffect(Unit) {
        DroppedFiles.files.collect {
            navController.navigate(Destinations.IMPORT) { launchSingleTop = true }
        }
    }

    AthenaContent(navController = navController, modifier = modifier)
}

@Composable
private fun AthenaContent(navController: NavHostController, modifier: Modifier) {
    AthenaTheme {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination
        val prefsStore = koinInject<ReaderPrefsStore>()
        val readerPrefs by prefsStore.prefs.collectAsStateWithLifecycle(ReaderPrefs())

        // An open book takes over the screen; the Read hub keeps the bar.
        val showBottomBar = topLevelDestinations.any { destination ->
            currentRoute?.hierarchy?.any { it.route == destination.route } == true
        }

        PapyrusBackdrop {
            Scaffold(
                modifier = modifier,
                containerColor = Color.Transparent,
                bottomBar = {
                    if (!showBottomBar) return@Scaffold
                    Column {
                        GreekKey(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 16.dp))
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                            tonalElevation = 0.dp,
                        ) {
                            val itemColors = athenaNavItemColors()
                            topLevelDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true,
                                    onClick = {
                                        if (destination.route == Destinations.READ) {
                                            val last = readerPrefs.lastBook
                                            if (last != null) navController.openInReader(last)
                                            else navController.switchTo(Destinations.READ)
                                        } else {
                                            navController.switchTo(destination.route)
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = stringResource(destination.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes).uppercase()) },
                                    colors = itemColors,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destinations.LIBRARY,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destinations.LIBRARY) {
                    val viewModel = koinViewModel<LibraryViewModel>()
                    LibraryScreen(
                        viewModel = viewModel,
                        onOpenBook = { navController.navigate(Destinations.book(it)) },
                        onResume = { navController.openInReader(it) },
                    )
                }

                composable(Destinations.READ) {
                    ReaderHubScreen(
                        viewModel = koinViewModel<ReaderHubViewModel>(),
                        onOpen = { navController.openInReader(it) },
                    )
                }

                composable(Destinations.HIGHLIGHTS) {
                    val viewModel = koinViewModel<HighlightsViewModel>()
                    HighlightsScreen(
                        viewModel = viewModel,
                        onOpenBook = { navController.navigate(Destinations.book(it)) },
                    )
                }

                composable(Destinations.IMPORT) {
                    val viewModel = koinViewModel<ImportViewModel>()
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        DroppedFiles.takePending()?.let { viewModel.load(it.name, it.bytes) }
                        DroppedFiles.files.collect {
                            DroppedFiles.takePending()
                            viewModel.load(it.name, it.bytes)
                        }
                    }

                    ImportScreen(
                        viewModel = viewModel,
                        onRead = { navController.openInReader(it) },
                        onPickFile = {
                            scope.launch {
                                runCatching { FilePickers.pick() }
                                    .onSuccess { file -> file?.let { viewModel.load(it.name, it.bytes) } }
                                    .onFailure { error ->
                                        viewModel.reportError(
                                            error.message ?: "Could not open the file.",
                                        )
                                    }
                            }
                        },
                    )
                }

                composable(Destinations.SETTINGS) {
                    SettingsScreen(viewModel = koinViewModel<SettingsViewModel>())
                }

                composable(Destinations.BOOK) { entry ->
                    val coordinate = entry.coordinateArg() ?: return@composable
                    BookDetailScreen(
                        coordinate = coordinate,
                        viewModel = koinViewModel<BookDetailViewModel>(),
                        onRead = { navController.openInReader(it) },
                        onBack = navController::popBackStack,
                    )
                }

                composable(Destinations.READER) { entry ->
                    val coordinate = entry.coordinateArg() ?: return@composable
                    ReaderScreen(
                        coordinate = coordinate,
                        viewModel = koinViewModel<ReaderViewModel>(),
                        onBack = navController::popBackStack,
                    )
                }
            }
            }
        }
    }
}

/** Lands on the Read tab then opens the book, so Back returns to the hub. */
private fun NavHostController.openInReader(coordinate: Coordinate) {
    navigate(Destinations.READ) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    navigate(Destinations.reader(coordinate)) { launchSingleTop = true }
}

/** Bottom-bar behaviour: single instance, state kept, no back-stack pile-up. */
private fun NavHostController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavBackStackEntry.coordinateArg() =
    arguments?.getString(Destinations.BOOK_ARG)?.let(Nip19::decodeNaddr)
