package com.athena.reader.nostr.signer

/**
 * `bunker://<remote-signer-pubkey>?relay=wss://...&secret=...`
 *
 * Parsed by hand because commonMain has no URI type and the shape is fixed. The
 * relay list is mandatory: without at least one relay there is no channel to
 * reach the signer on, so a URI without one is rejected rather than accepted and
 * left to fail later with a timeout.
 */
data class BunkerUri(
    val remoteSignerPubkey: String,
    val relays: List<String>,
    val secret: String?,
) {
    companion object {
        private const val SCHEME = "bunker://"

        fun parse(value: String): BunkerUri? {
            val trimmed = value.trim()
            if (!trimmed.startsWith(SCHEME)) return null

            val body = trimmed.removePrefix(SCHEME)
            val pubkey = body.substringBefore('?').takeIf { it.isHex64() } ?: return null

            val query = body.substringAfter('?', missingDelimiterValue = "")
            val params = query.split('&')
                .filter(String::isNotBlank)
                .map { it.substringBefore('=') to percentDecode(it.substringAfter('=', "")) }

            val relays = params.filter { it.first == "relay" }
                .map { it.second }
                .filter(String::isNotBlank)
            if (relays.isEmpty()) return null

            return BunkerUri(pubkey, relays, params.firstOrNull { it.first == "secret" }?.second)
        }

        private fun String.isHex64(): Boolean =
            length == 64 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        private fun percentDecode(value: String): String {
            if ('%' !in value && '+' !in value) return value

            val bytes = mutableListOf<Byte>()
            var index = 0
            while (index < value.length) {
                val char = value[index]
                val hex = if (char == '%' && index + 2 < value.length) {
                    value.substring(index + 1, index + 3).toIntOrNull(16)
                } else {
                    null
                }
                when {
                    hex != null -> {
                        bytes.add(hex.toByte())
                        index += 3
                    }

                    char == '+' -> {
                        bytes.add(' '.code.toByte())
                        index++
                    }

                    else -> {
                        char.toString().encodeToByteArray().forEach(bytes::add)
                        index++
                    }
                }
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}
