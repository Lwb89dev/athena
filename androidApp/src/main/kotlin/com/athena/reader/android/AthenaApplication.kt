package com.athena.reader.android

import android.app.Application
import com.athena.reader.di.SessionBootstrap
import com.athena.reader.di.appModules
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.AndroidPlatform
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AthenaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Everything downstream (Room path, DataStore path, NIP-55 intents)
        // needs this, so it is installed before Koin builds anything.
        AndroidPlatform.install(this)

        startKoin {
            androidContext(this@AthenaApplication)
            modules(appModules())
        }

        get<SessionBootstrap>().start(get<SignerManager>())
    }
}
