package com.athena.reader.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bridges the Storage Access Framework's document picker to a suspending
 * call, the same shape as ExternalSignerLauncher: only one pick can be in
 * flight, so a second tap while the chooser is open waits instead of racing
 * for the same result.
 *
 * The picker asks for the wildcard mime type rather than a book-specific
 * one: document providers routinely report .epub/.adoc as
 * application/octet-stream or leave the type blank, so filtering by mime
 * type hides exactly the files this screen wants.
 */
class AndroidFilePicker {

    private val mutex = Mutex()
    private var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    /** Call from Activity.onCreate, before the first composition. */
    fun registerWith(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pending?.complete(uri)
            pending = null
        }
    }

    fun unregister() {
        pending?.complete(null)
        pending = null
        launcher = null
    }

    suspend fun pick(): PickedFile? = mutex.withLock {
        val target = launcher ?: return null
        val deferred = CompletableDeferred<Uri?>()
        pending = deferred
        runCatching { target.launch("*/*") }.onFailure {
            pending = null
            return null
        }
        val uri = deferred.await() ?: return null
        withContext(Dispatchers.IO) { readUri(uri) }
    }

    private fun readUri(uri: Uri): PickedFile? {
        val context = AndroidPlatform.applicationContext
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return PickedFile(displayNameOf(context, uri), bytes)
    }

    /**
     * A content Uri's last path segment is an opaque document id, not a file
     * name — the real name (and its extension, which ImportViewModel picks
     * an importer by) only comes from the DISPLAY_NAME column.
     */
    private fun displayNameOf(context: Context, uri: Uri): String {
        val queried = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        return queried?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "import"
    }
}
