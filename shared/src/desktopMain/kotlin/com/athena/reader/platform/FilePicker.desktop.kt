package com.athena.reader.platform

import java.awt.Component
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Swing chooser, not AWT FileDialog. FileDialog uses the GTK native peer and
 * SIGSEGVs when Compose's Skia window is also on the display — that is the
 * crash on the upload button.
 *
 * Shown on the EDT; the coroutine waits on [Dispatchers.IO] so we never call
 * `invokeAndWait` from the EDT itself (deadlock).
 */
fun desktopFilePicker(owner: Component? = null): suspend () -> PickedFile? = {
    withContext(Dispatchers.IO) { pickBookFile(owner) }
}

private suspend fun pickBookFile(owner: Component?): PickedFile? =
    suspendCancellableCoroutine { cont ->
        SwingUtilities.invokeLater {
            if (!cont.isActive) return@invokeLater
            runCatching { chooseFile(owner) }
                .onSuccess { file -> if (cont.isActive) cont.resume(file) }
                .onFailure { error -> if (cont.isActive) cont.resumeWithException(error) }
        }
    }

private fun chooseFile(owner: Component?): PickedFile? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Choose a book"
    chooser.fileFilter = FileNameExtensionFilter(
        "Books (EPUB, PDF, text)",
        "epub", "pdf", "txt", "md", "markdown", "adoc", "asciidoc", "text",
    )
    if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile ?: return null
    return readPickedFile(file)
}

fun readPickedFile(file: File): PickedFile {
    if (!file.isFile) throw IllegalArgumentException("That path is not a file.")
    if (file.length() > Limits.MAX_FILE_BYTES) {
        val mb = Limits.MAX_FILE_BYTES / (1024 * 1024)
        throw IllegalArgumentException("That file is larger than $mb MB.")
    }
    return PickedFile(file.name, file.readBytes())
}
