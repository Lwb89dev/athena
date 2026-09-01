package com.athena.reader.nostr.model
import com.athena.reader.platform.nowSeconds

import com.athena.reader.nostr.crypto.EventId
import com.athena.reader.nostr.crypto.Schnorr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A NIP-01 event. Field names match the wire format exactly so the same class
 * round-trips through relays, the Amber signer and our local cache.
 */
@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** First value of the first tag named [name], or null. */
    fun tag(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

    /** All second values of tags named [name], in event order. */
    fun tagValues(name: String): List<String> =
        tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    /** The `d` tag that identifies an addressable (30000..39999) event. */
    val dTag: String? get() = tag("d")

    val isAddressable: Boolean get() = kind in 30_000..39_999

    /** `kind:pubkey:d` — the NIP-01 coordinate used by `a` tags. */
    fun coordinate(): Coordinate? =
        if (isAddressable) Coordinate(kind, pubkey, dTag.orEmpty()) else null

    /** Recomputes the id and checks the schnorr signature. Never trust a relay. */
    fun verify(): Boolean = id == EventId.compute(this) && Schnorr.verify(id, pubkey, sig)
}

/**
 * An event that has not been signed yet. The signer (Amber / bunker) fills in
 * `id`, `pubkey` and `sig`; until then those fields simply do not exist.
 */
@Serializable
data class UnsignedEvent(
    @SerialName("created_at") val createdAt: Long = nowSeconds(),
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String,
)
