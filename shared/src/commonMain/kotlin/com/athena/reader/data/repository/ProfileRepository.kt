package com.athena.reader.data.repository

import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.platform.Log
import com.athena.reader.platform.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A NIP-01 kind 0 profile, reduced to what a library actually shows. */
data class Profile(
    val pubkey: String,
    val name: String?,
    val about: String?,
    val pictureUrl: String?,
    val nip05: String?,
) {
    /** Best label for this author, falling back to a short npub. */
    val displayName: String
        get() = name?.takeIf(String::isNotBlank) ?: shortNpub()

    private fun shortNpub(): String {
        val npub = runCatching { Nip19.encodeNpub(pubkey) }.getOrNull() ?: pubkey
        return npub.take(12) + "…"
    }
}

/**
 * Author profiles.
 *
 * Book events carry an `author` *tag*, which is just a string the publisher
 * typed. The real identity is the kind 0 event of the pubkey that signed it,
 * and that is what this resolves — batched, because a library screen would
 * otherwise open one subscription per row.
 */
class ProfileRepository(
    private val relayPool: RelayPool,
    private val json: Json,
) {
    private val cache = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val profiles: StateFlow<Map<String, Profile>> = cache

    private val lock = Mutex()
    private val lastAttempt = mutableMapOf<String, Long>()

    /** Fetches any profile we do not have yet, in one subscription. */
    suspend fun prefetch(pubkeys: Collection<String>) {
        val wanted = lock.withLock {
            val now = nowMillis()
            pubkeys.distinct()
                .filter { it.length == 64 }
                .filterNot { cache.value.containsKey(it) }
                .filter { now - (lastAttempt[it] ?: 0) > RETRY_AFTER_MILLIS }
                .onEach { lastAttempt[it] = now }
        }
        if (wanted.isEmpty()) return

        val events = relayPool.fetch(
            listOf(Filter(authors = wanted, kinds = listOf(Kinds.METADATA))),
        )
        // Relays hand back every revision; only the newest per author counts.
        val newest = events.groupBy { it.pubkey }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }

        val parsed = newest.mapNotNull { event -> parse(event.pubkey, event.content) }
        if (parsed.isEmpty()) return

        cache.value = cache.value + parsed.associateBy(Profile::pubkey)
    }

    suspend fun get(pubkey: String): Profile? {
        cache.value[pubkey]?.let { return it }
        prefetch(listOf(pubkey))
        return cache.value[pubkey]
    }

    private fun parse(pubkey: String, content: String): Profile? {
        // Kind 0 content is JSON by spec, but relays carry whatever was signed:
        // empty strings and truncated objects both occur in the wild. Log enough
        // to tell the two apart instead of a bare "not JSON".
        if (content.isBlank()) return null

        val body = runCatching { json.parseToJsonElement(content) as? JsonObject }.getOrElse {
            Log.d(TAG, "profile of $pubkey is malformed: ${content.take(40)}")
            null
        } ?: return null

        return Profile(
            pubkey = pubkey,
            name = body.string("display_name") ?: body.string("name"),
            about = body.string("about"),
            pictureUrl = body.string("picture"),
            nip05 = body.string("nip05"),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private companion object {
        const val TAG = "ProfileRepository"

        /** Do not hammer relays for a profile that simply does not exist. */
        const val RETRY_AFTER_MILLIS = 10 * 60 * 1000L
    }
}
