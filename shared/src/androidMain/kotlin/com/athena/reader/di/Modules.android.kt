package com.athena.reader.di

import com.athena.reader.nostr.signer.AndroidSignerFactory
import com.athena.reader.nostr.signer.ExternalSignerLauncher
import com.athena.reader.nostr.signer.PlatformSignerFactory
import com.athena.reader.platform.AndroidFilePicker
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single { ExternalSignerLauncher() }
    single { AndroidFilePicker() }
    single<PlatformSignerFactory> { AndroidSignerFactory(get(), get()) }
}
