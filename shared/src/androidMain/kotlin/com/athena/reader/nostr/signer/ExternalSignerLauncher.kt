package com.athena.reader.nostr.signer

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges NIP-55's intent round-trip to a suspending call.
 *
 * Amber answers one intent at a time, so requests are serialised behind a mutex;
 * without that, two overlapping sign requests would race for the same result.
 */
class ExternalSignerLauncher {

    private val mutex = Mutex()
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var pending: CompletableDeferred<Intent?>? = null

    /** Call from Activity.onCreate, before the first composition. */
    fun registerWith(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pending?.complete(result.data)
            pending = null
        }
    }

    fun unregister() {
        pending?.complete(null)
        pending = null
        launcher = null
    }

    /** Returns the result intent, or null if the signer was dismissed or missing. */
    suspend fun request(intent: Intent): Intent? = mutex.withLock {
        val target = launcher ?: return null
        val deferred = CompletableDeferred<Intent?>()
        pending = deferred
        runCatching { target.launch(intent) }.onFailure {
            pending = null
            return null
        }
        deferred.await()
    }
}
