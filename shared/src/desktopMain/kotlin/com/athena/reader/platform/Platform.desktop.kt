package com.athena.reader.platform

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.security.SecureRandom

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

private val secureRandom = SecureRandom()

actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

actual fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

actual fun nowSeconds(): Long = System.currentTimeMillis() / 1000

actual fun nowMillis(): Long = System.currentTimeMillis()

/** XDG on Linux, AppData on Windows, Application Support on macOS. */
actual fun appDataDirectory(): String {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val directory = when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: home, "Athena")
        os.contains("mac") -> File(home, "Library/Application Support/Athena")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "athena")
    }
    directory.mkdirs()
    return directory.absolutePath
}

actual fun platformLog(level: LogLevel, tag: String, message: String, error: Throwable?) {
    val stream = if (level == LogLevel.Debug) System.out else System.err
    stream.println("[${level.name.uppercase()}] $tag: $message")
    error?.printStackTrace(stream)
}

actual fun extractPdf(bytes: ByteArray): ExtractedPdf? {
    val document = runCatching {
        org.apache.pdfbox.pdmodel.PDDocument.load(bytes)
    }.getOrNull() ?: return null

    document.use { pdf ->
        val info = pdf.documentInformation
        val stripper = org.apache.pdfbox.text.PDFTextStripper().apply {
            sortByPosition = true
        }
        val text = runCatching { stripper.getText(pdf) }.getOrNull()?.trim().orEmpty()
        if (text.isBlank()) return null
        return ExtractedPdf(
            title = info.title?.trim()?.takeIf(String::isNotBlank),
            author = info.author?.trim()?.takeIf(String::isNotBlank),
            text = text,
        )
    }
}

actual fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> = readZipEntriesBounded(bytes)

internal fun readZipEntriesBounded(bytes: ByteArray): Map<String, ByteArray> {
    if (bytes.size > Limits.MAX_FILE_BYTES) return emptyMap()
    val entries = LinkedHashMap<String, ByteArray>()
    var total = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (entries.size < Limits.MAX_ZIP_ENTRIES) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory || !isSafeArchivePath(entry.name)) {
                zip.closeEntry()
                continue
            }
            val data = zip.readAtMost(Limits.MAX_ZIP_ENTRY_BYTES)
            zip.closeEntry()
            if (data == null) continue
            total += data.size
            if (total > Limits.MAX_ZIP_TOTAL_BYTES) break
            entries[entry.name] = data
        }
    }
    return entries
}

/** Null when the entry is larger than [max] — the rest of that entry is skipped. */
private fun ZipInputStream.readAtMost(max: Int): ByteArray? {
    val buffer = ByteArray(8192)
    val out = ByteArrayOutputStream()
    while (true) {
        val n = read(buffer)
        if (n <= 0) return out.toByteArray()
        out.write(buffer, 0, n)
        if (out.size() > max) return null
    }
}
