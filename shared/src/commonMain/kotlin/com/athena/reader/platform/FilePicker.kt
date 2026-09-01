package com.athena.reader.platform

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A file the user handed us, name included so the importer can be chosen. */
data class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * Choosing a file is the one part of the import flow that cannot be shared:
 * Android goes through the storage access framework, the desktop through a
 * native dialog. Each platform installs its own picker here at startup.
 */
object FilePickers {
    private var picker: (suspend () -> PickedFile?)? = null

    fun install(implementation: suspend () -> PickedFile?) {
        picker = implementation
    }

    /** Null when no picker is installed, or the user cancelled. */
    suspend fun pick(): PickedFile? = picker?.invoke()
}

/**
 * Files dropped onto the window, from wherever the platform catches them.
 *
 * A channel rather than a callback because the drop happens outside Compose —
 * the AWT event loop on the desktop, an intent on Android — and the screen that
 * consumes it may not exist yet when the drop lands.
 */
object DroppedFiles {
    /**
     * No replay: keeping the last file's bytes in the flow forever was a memory
     * leak of whatever the user last dropped. [pending] covers the race where
     * the drop navigates to the import screen before that screen is collecting.
     */
    private val _files = MutableSharedFlow<PickedFile>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val files: SharedFlow<PickedFile> = _files

    @Volatile
    private var pending: PickedFile? = null

    fun offer(file: PickedFile) {
        pending = file
        _files.tryEmit(file)
    }

    fun takePending(): PickedFile? {
        val value = pending
        pending = null
        return value
    }
}
