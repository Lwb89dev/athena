package com.athena.reader.nostr.crypto

import com.athena.reader.nostr.model.Coordinate

/**
 * NIP-19 bech32 entities. We need `npub` for display and `naddr` because that is
 * how a book is linked to from outside the app (`nostr:naddr1...`).
 */
object Nip19 {

    private const val TLV_SPECIAL: Int = 0
    private const val TLV_RELAY: Int = 1
    private const val TLV_AUTHOR: Int = 2
    private const val TLV_KIND: Int = 3

    fun encodeNpub(pubkeyHex: String): String = Bech32.encode("npub", pubkeyHex.hexToBytes())

    fun decodeNpub(npub: String): String? {
        val (hrp, data) = Bech32.decode(npub) ?: return null
        return if (hrp == "npub" && data.size == 32) data.toHex() else null
    }

    fun encodeNaddr(coordinate: Coordinate, relays: List<String> = emptyList()): String {
        val out = mutableListOf<Byte>()
        val ident = coordinate.identifier.encodeToByteArray().let { bytes ->
            if (bytes.size <= 255) bytes else bytes.copyOf(255)
        }
        out.writeTlv(TLV_SPECIAL, ident)
        relays.forEach { out.writeTlv(TLV_RELAY, it.encodeToByteArray()) }
        out.writeTlv(TLV_AUTHOR, coordinate.pubkey.hexToBytes())
        out.writeTlv(TLV_KIND, coordinate.kind.toBigEndianBytes())
        return Bech32.encode("naddr", out.toByteArray())
    }

    fun decodeNaddr(naddr: String): Coordinate? {
        val (hrp, data) = Bech32.decode(naddr) ?: return null
        if (hrp != "naddr") return null

        var identifier: String? = null
        var author: String? = null
        var kind: Int? = null
        forEachTlv(data) { type, value ->
            when (type) {
                TLV_SPECIAL -> identifier = value.decodeToString()
                TLV_AUTHOR -> author = value.toHex()
                TLV_KIND -> kind = value.toBigEndianInt()
            }
        }
        val pubkey = author ?: return null
        return Coordinate(kind ?: return null, pubkey, identifier ?: return null)
    }

    /** Accepts `naddr1...`, `nostr:naddr1...` and `athena://book/naddr1...`. */
    fun coordinateFromUri(uri: String): Coordinate? {
        val token = uri.substringAfterLast('/').substringAfter("nostr:")
        return decodeNaddr(token)
    }

    private inline fun forEachTlv(data: ByteArray, onEntry: (type: Int, value: ByteArray) -> Unit) {
        var offset = 0
        while (offset + 2 <= data.size) {
            val type = data[offset].toInt() and 0xFF
            val length = data[offset + 1].toInt() and 0xFF
            val start = offset + 2
            if (start + length > data.size) return
            onEntry(type, data.copyOfRange(start, start + length))
            offset = start + length
        }
    }

    private fun MutableList<Byte>.writeTlv(type: Int, value: ByteArray) {
        add(type.toByte())
        add(value.size.toByte())
        addAll(value.toList())
    }

    private fun Int.toBigEndianBytes() = byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr 8).toByte(),
        this.toByte(),
    )

    private fun ByteArray.toBigEndianInt(): Int = fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
}
