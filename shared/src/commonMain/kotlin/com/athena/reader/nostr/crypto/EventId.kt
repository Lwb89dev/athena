package com.athena.reader.nostr.crypto

import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.platform.sha256

/**
 * NIP-01 event ids: sha256 over the UTF-8 bytes of a *canonical* JSON array
 *
 *     [0, pubkey, created_at, kind, tags, content]
 *
 * serialised with no whitespace and with exactly the six escape sequences the
 * spec allows. A general-purpose JSON encoder is not usable here because it may
 * escape more than that (e.g. non-ASCII as \\uXXXX) and produce a different hash.
 */
object EventId {

    fun compute(event: NostrEvent): String =
        compute(event.pubkey, event.createdAt, event.kind, event.tags, event.content)

    fun compute(pubkey: String, event: UnsignedEvent): String =
        compute(pubkey, event.createdAt, event.kind, event.tags, event.content)

    fun compute(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
    ): String {
        val canonical = canonicalJson(pubkey, createdAt, kind, tags, content)
        return sha256(canonical.encodeToByteArray()).toHex()
    }

    fun canonicalJson(
        pubkey: String,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String,
    ): String = buildString {
        append("[0,\"").append(pubkey).append("\",").append(createdAt).append(',').append(kind).append(",[")
        tags.forEachIndexed { tagIndex, tag ->
            if (tagIndex > 0) append(',')
            append('[')
            tag.forEachIndexed { valueIndex, value ->
                if (valueIndex > 0) append(',')
                appendEscaped(value)
            }
            append(']')
        }
        append("],")
        appendEscaped(content)
        append(']')
    }

    /** The only escapes NIP-01 permits; everything else goes through verbatim. */
    private fun StringBuilder.appendEscaped(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(char)
            }
        }
        append('"')
    }
}

fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val byte = this[i].toInt() and 0xFF
        out[i * 2] = HEX_DIGITS[byte ushr 4]
        out[i * 2 + 1] = HEX_DIGITS[byte and 0x0F]
    }
    return String(out)
}

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && length in 2..256) { "hex string must have an even length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val high = this[i * 2].hexDigit()
        val low = this[i * 2 + 1].hexDigit()
        require(high >= 0 && low >= 0) { "not a hex string" }
        out[i] = ((high shl 4) or low).toByte()
    }
    return out
}

private fun Char.hexDigit(): Int = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> -1
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()
