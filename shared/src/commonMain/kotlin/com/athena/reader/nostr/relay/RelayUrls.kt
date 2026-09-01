package com.athena.reader.nostr.relay

/**
 * Relay URLs are user-supplied and then opened as websockets. Anything that is
 * not a plain `ws://` / `wss://` host is refused: credentials in the URL would
 * leak on the wire, and other schemes are an open redirect into the HTTP client.
 */
fun parseRelayUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.length !in 8..200) return null
    if ('@' in trimmed || ' ' in trimmed || '\n' in trimmed || '\r' in trimmed) return null

    val rest = when {
        trimmed.startsWith("wss://") -> trimmed.substring(6)
        trimmed.startsWith("ws://") -> trimmed.substring(5)
        else -> return null
    }
    if (rest.isBlank() || rest.startsWith("/")) return null

    val host = rest.substringBefore('/').substringBefore('?')
    if (host.isBlank() || host.startsWith(":")) return null
    return trimmed
}
