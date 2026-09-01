package com.athena.reader.platform

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Blocking IO dispatcher. `Dispatchers.IO` is not part of the common coroutines
 * API, so the two JVM targets each hand theirs over here.
 */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Cryptographically secure random bytes. Used for NIP-44 nonces, BIP-340 aux
 * randomness and NIP-46 ephemeral keys — a weak source here breaks all three.
 */
expect fun randomBytes(size: Int): ByteArray

/**
 * Reads a zip archive into `entry path -> bytes`. EPUB is a zip, and there is
 * no zip in the common standard library.
 *
 * Whole-archive: books are tens of megabytes at worst, and streaming would buy
 * nothing while complicating every caller.
 */
expect fun readZipEntries(bytes: ByteArray): Map<String, ByteArray>

/**
 * Plain text pulled out of a PDF. Null when the file is not a PDF, has no
 * extractable text (a scan, typically), or the parser refuses it.
 *
 * Lives here rather than in the importer because PDFBox is JVM-only; commonMain
 * never sees the library.
 */
data class ExtractedPdf(
    val title: String?,
    val author: String?,
    val text: String,
)

expect fun extractPdf(bytes: ByteArray): ExtractedPdf?

/** SHA-256, the one primitive nostr cannot be expressed without. */
expect fun sha256(bytes: ByteArray): ByteArray

/** Seconds since the epoch — nostr's `created_at` unit. */
expect fun nowSeconds(): Long

expect fun nowMillis(): Long

/** Where DataStore and the Room file live on this platform. */
expect fun appDataDirectory(): String

enum class LogLevel { Debug, Warn, Error }

/**
 * A logger thin enough to live in commonMain. Logcat on Android, stderr on the
 * desktop — the call sites do not care which.
 */
expect fun platformLog(level: LogLevel, tag: String, message: String, error: Throwable? = null)

object Log {
    fun d(tag: String, message: String) = platformLog(LogLevel.Debug, tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) =
        platformLog(LogLevel.Warn, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) =
        platformLog(LogLevel.Error, tag, message, error)
}
