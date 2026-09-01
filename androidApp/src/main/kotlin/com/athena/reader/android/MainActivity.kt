package com.athena.reader.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.signer.ExternalSignerLauncher
import com.athena.reader.platform.AndroidFilePicker
import com.athena.reader.platform.FilePickers
import com.athena.reader.ui.AthenaApp
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    private val signerLauncher: ExternalSignerLauncher by lazy { get() }
    private val filePicker: AndroidFilePicker by lazy { get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Both answer through onActivityResult, so the launchers have to be
        // registered before the activity is STARTED — never lazily from a click.
        signerLauncher.registerWith(this)
        filePicker.registerWith(this)
        FilePickers.install(filePicker::pick)

        setContent {
            // A link that arrives while we are already running replaces this,
            // which is why it is state rather than a value read once.
            var pending by remember { mutableStateOf(coordinateOf(intent)) }

            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { pending = coordinateOf(it) ?: pending }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }

            AthenaApp(openBook = pending)
        }
    }

    private fun coordinateOf(intent: Intent?): Coordinate? =
        intent?.data?.toString()?.let(Nip19::coordinateFromUri)

    override fun onDestroy() {
        signerLauncher.unregister()
        filePicker.unregister()
        super.onDestroy()
    }
}
