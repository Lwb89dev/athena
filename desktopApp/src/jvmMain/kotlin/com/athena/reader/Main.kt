package com.athena.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.athena.reader.di.SessionBootstrap
import com.athena.reader.di.appModules
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.DroppedFiles
import com.athena.reader.platform.FilePickers
import com.athena.reader.platform.PickedFile
import com.athena.reader.platform.desktopFilePicker
import com.athena.reader.ui.AthenaApp
import org.koin.core.context.startKoin
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
fun main() {
    val koin = startKoin { modules(appModules()) }.koin
    koin.get<SessionBootstrap>().start(koin.get<SignerManager>())

    application {
        val windowState = rememberWindowState(size = DpSize(1_100.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Project Athena",
        ) {
            DisposableEffect(window) {
                FilePickers.install(desktopFilePicker(window))
                onDispose { }
            }
            // Built inside the composition so `remember` is legal; a Modifier
            // extension would need `composed`, which is deprecated.
            val dropTarget = rememberBookDropTarget()
            AthenaApp(
                modifier = Modifier.dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = dropTarget,
                ),
            )
        }
    }
}

/**
 * Drag-and-drop onto the whole window.
 *
 * Attached at the top level rather than to the import screen alone: dropping a
 * book anywhere in the app is the gesture people expect, and routing it to the
 * import flow is our job, not theirs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberBookDropTarget(): DragAndDropTarget = remember {
    object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val transferable = event.awtTransferable
            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false

            @Suppress("UNCHECKED_CAST")
            val files = runCatching {
                transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            }.getOrNull().orEmpty()

            val first = files.firstOrNull() ?: return false
            val picked = runCatching {
                com.athena.reader.platform.readPickedFile(first)
            }.getOrNull() ?: return false
            DroppedFiles.offer(picked)
            return true
        }
    }
}
