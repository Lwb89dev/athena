package com.athena.reader.nostr.crypto

/**
 * Minimal BIP-173 bech32 codec. NIP-19 identifiers can be far longer than the
 * 90-character bech32 limit (naddr with relay hints), so the length cap is not
 * enforced — that is deliberate and matches every other nostr implementation.
 */
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun encode(hrp: String, data: ByteArray): String {
        val words = convertBits(data, 8, 5, pad = true)
        val checksum = createChecksum(hrp, words)
        val body = (words + checksum).map { CHARSET[it.toInt()] }.joinToString("")
        return "${hrp}1$body"
    }

    /** Returns hrp to payload bytes, or null if the string is not valid bech32. */
    fun decode(input: String): Pair<String, ByteArray>? {
        val normalized = input.lowercase()
        val separator = normalized.lastIndexOf('1')
        if (separator < 1 || separator + 7 > normalized.length) return null

        val hrp = normalized.substring(0, separator)
        val words = ByteArray(normalized.length - separator - 1)
        for (i in words.indices) {
            val index = CHARSET.indexOf(normalized[separator + 1 + i])
            if (index < 0) return null
            words[i] = index.toByte()
        }
        if (polymod(expandHrp(hrp) + words.toList().map { it.toInt() }) != 1) return null

        val payload = words.copyOfRange(0, words.size - 6)
        return hrp to convertBits(payload, 5, 8, pad = false)
    }

    private fun createChecksum(hrp: String, words: ByteArray): ByteArray {
        val values = expandHrp(hrp) + words.map { it.toInt() } + List(6) { 0 }
        val mod = polymod(values) xor 1
        return ByteArray(6) { i -> ((mod ushr (5 * (5 - i))) and 31).toByte() }
    }

    private fun expandHrp(hrp: String): List<Int> =
        hrp.map { it.code ushr 5 } + listOf(0) + hrp.map { it.code and 31 }

    private fun polymod(values: List<Int>): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (i in generators.indices) {
                if ((top ushr i) and 1 == 1) checksum = checksum xor generators[i]
            }
        }
        return checksum
    }

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): ByteArray {
        var accumulator = 0
        var bits = 0
        val out = ArrayList<Byte>(data.size * from / to + 2)
        val maxValue = (1 shl to) - 1

        for (byte in data) {
            accumulator = (accumulator shl from) or (byte.toInt() and 0xFF)
            bits += from
            while (bits >= to) {
                bits -= to
                out.add(((accumulator ushr bits) and maxValue).toByte())
            }
        }
        if (pad && bits > 0) out.add(((accumulator shl (to - bits)) and maxValue).toByte())
        return out.toByteArray()
    }
}
