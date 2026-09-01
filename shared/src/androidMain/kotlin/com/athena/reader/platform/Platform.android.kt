package com.athena.reader.platform

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.security.SecureRandom

/**
 * Android needs an application Context for its data directory. It is set once
 * from Application.onCreate, before anything else in the graph is built.
 */
@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var applicationContext: Context
        private set

    fun install(context: Context) {
        applicationContext = context.applicationContext
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
    }
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

private val secureRandom = SecureRandom()

actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

actual fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

actual fun nowSeconds(): Long = System.currentTimeMillis() / 1000

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun appDataDirectory(): String = AndroidPlatform.applicationContext.filesDir.absolutePath

actual fun platformLog(level: LogLevel, tag: String, message: String, error: Throwable?) {
    when (level) {
        LogLevel.Debug -> android.util.Log.d(tag, message, error)
        LogLevel.Warn -> android.util.Log.w(tag, message, error)
        LogLevel.Error -> android.util.Log.e(tag, message, error)
    }
}

actual fun extractPdf(bytes: ByteArray): ExtractedPdf? {
    val document = runCatching {
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes)
    }.getOrNull() ?: return null

    document.use { pdf ->
        val info = pdf.documentInformation
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
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
