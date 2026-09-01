package com.athena.reader.di

import com.athena.reader.data.local.createDatabase
import com.athena.reader.data.repository.FavoriteRepository
import com.athena.reader.data.importer.BookPublisher
import com.athena.reader.data.repository.FollowRepository
import com.athena.reader.data.repository.HighlightRepository
import com.athena.reader.data.repository.LibraryRepository
import com.athena.reader.data.repository.ProfileRepository
import com.athena.reader.data.repository.ProgressRepository
import com.athena.reader.data.session.ReaderPrefsStore
import com.athena.reader.data.session.SessionStore
import com.athena.reader.data.sync.EncryptedSync
import com.athena.reader.data.sync.SyncSecret
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.createDataStore
import com.athena.reader.platform.ioDispatcher
import com.athena.reader.ui.book.BookDetailViewModel
import com.athena.reader.ui.highlights.HighlightsViewModel
import com.athena.reader.ui.importer.ImportViewModel
import com.athena.reader.ui.library.LibraryViewModel
import com.athena.reader.ui.onboarding.OnboardingViewModel
import com.athena.reader.ui.reader.ReaderHubViewModel
import com.athena.reader.ui.reader.ReaderViewModel
import com.athena.reader.ui.settings.SettingsViewModel
import com.athena.reader.data.local.AthenaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Provided by each platform: HTTP engine choice, signer factory, file paths. */
expect fun platformModule(): Module

val coreModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }

    single {
        HttpClient(get<HttpClientEngine>()) {
            install(WebSockets) {
                maxFrameSize = com.athena.reader.platform.Limits.MAX_WS_FRAME_BYTES
            }
        }
    }

    /** Outlives every screen: relays and the signer must survive navigation. */
    single { CoroutineScope(SupervisorJob() + ioDispatcher) }

    single { RelayPool(get(), get()) }
    single { createDataStore() }
    single { SessionStore(get()) }
    single { ReaderPrefsStore(get()) }
    single { SignerManager(get(), get(), get(), get(), get()) }

    single { createDatabase() }
    single { get<AthenaDatabase>().bookDao() }
    single { get<AthenaDatabase>().sectionDao() }
    single { get<AthenaDatabase>().highlightDao() }
    single { get<AthenaDatabase>().progressDao() }
    single { get<AthenaDatabase>().favoriteDao() }
    single { get<AthenaDatabase>().syncStateDao() }

    single { SyncSecret(get(), get(), get(), get()) }
    single { EncryptedSync(get(), get(), get(), get()) }

    single { LibraryRepository(get(), get(), get()) }
    single { HighlightRepository(get(), get(), get(), get(), get()) }
    single { ProgressRepository(get(), get(), get(), get()) }
    single { FavoriteRepository(get(), get(), get(), get(), get(), get()) }
    single { ProfileRepository(get(), get()) }
    single { FollowRepository(get(), get()) }
    single { BookPublisher(get(), get(), get(), get(), get()) }

    single { SessionBootstrap(get(), get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get()) }
    viewModel { BookDetailViewModel(get(), get(), get(), get(), get()) }
    viewModel { ReaderViewModel(get(), get(), get(), get(), get()) }
    viewModel { ReaderHubViewModel(get(), get(), get()) }
    viewModel { HighlightsViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { ImportViewModel(get(), get()) }
}

fun appModules(): List<Module> = listOf(platformModule(), coreModule, viewModelModule)
