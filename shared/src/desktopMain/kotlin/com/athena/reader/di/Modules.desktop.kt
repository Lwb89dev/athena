package com.athena.reader.di

import com.athena.reader.nostr.signer.DesktopSignerFactory
import com.athena.reader.nostr.signer.PlatformSignerFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<HttpClientEngine> { Java.create() }
    single<PlatformSignerFactory> { DesktopSignerFactory() }
}
