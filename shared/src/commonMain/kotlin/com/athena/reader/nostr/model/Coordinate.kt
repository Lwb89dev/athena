package com.athena.reader.nostr.model

/**
 * NIP-01 address of a replaceable event: `kind:pubkey:d-tag`.
 * This is the stable identity of a book across edits, so it is what we key
 * favorites, highlights and reading progress on.
 */
data class Coordinate(
    val kind: Int,
    val pubkey: String,
    val identifier: String,
) {
    fun asString(): String = "$kind:$pubkey:$identifier"

    /** The `a` tag form, optionally hinting a relay. */
    fun asTag(relayHint: String? = null): List<String> =
        if (relayHint == null) listOf("a", asString()) else listOf("a", asString(), relayHint)

    companion object {
        fun parse(value: String): Coordinate? {
            val parts = value.split(":", limit = 3)
            if (parts.size != 3) return null
            val kind = parts[0].toIntOrNull() ?: return null
            val pubkey = parts[1]
            val identifier = parts[2]
            if (pubkey.length != 64) return null
            if (identifier.length > com.athena.reader.platform.Limits.MAX_COORDINATE_D_CHARS) return null
            if (pubkey.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
            return Coordinate(kind, pubkey.lowercase(), identifier)
        }
    }
}
