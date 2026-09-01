package com.athena.reader.data.repository

import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A NIP-51 follow set (kind 30000) — a named group of people. */
data class PeopleList(
    val coordinate: Coordinate,
    val title: String,
    val pubkeys: List<String>,
)

/**
 * Who the user follows, and which curated lists they keep.
 *
 * This exists because a global long-form feed on nostr is unusable as a
 * library: kind 30023 is wide open and a large share of it is spam, much of it
 * pornographic. No amount of keyword filtering fixes that honestly. The social
 * graph does: showing what people you chose to follow have published turns an
 * open sewer into a reading list, without anyone appointing themselves censor.
 *
 * Two sources, both standard:
 *  - **NIP-02** kind 3, the contact list every nostr client already maintains;
 *  - **NIP-51** kind 30000 follow sets, for topic-specific curation ("classics",
 *    "philosophy") that does not have to pollute the main follow list.
 */
class FollowRepository(
    private val relayPool: RelayPool,
    private val signerManager: SignerManager,
) {
    private val _follows = MutableStateFlow<List<String>>(emptyList())
    val follows: StateFlow<List<String>> = _follows

    private val _lists = MutableStateFlow<List<PeopleList>>(emptyList())
    val lists: StateFlow<List<PeopleList>> = _lists

    /**
     * Two hops out: people our follows follow, and people who follow our
     * follows. Direct contacts are excluded so the feed is the neighbourhood,
     * not a duplicate of Following.
     */
    private val _acquaintances = MutableStateFlow<List<String>>(emptyList())
    val acquaintances: StateFlow<List<String>> = _acquaintances

    /** Refreshes both sources. Safe to call on every app start. */
    suspend fun refresh() {
        val pubkey = signerManager.current.value.pubkeyHex ?: return

        val events = relayPool.fetch(
            listOf(
                Filter(authors = listOf(pubkey), kinds = listOf(Kinds.CONTACTS), limit = 1),
                Filter(authors = listOf(pubkey), kinds = listOf(Kinds.FOLLOW_SET), limit = 50),
            ),
        )

        // Relays return every revision of a replaceable event; only the newest counts.
        events.filter { it.kind == Kinds.CONTACTS }
            .maxByOrNull { it.createdAt }
            ?.let {
                _follows.value = it.tagValues("p")
                    .filter { key -> key.length == 64 }
                    .distinct()
                    .take(com.athena.reader.platform.Limits.MAX_FOLLOW_AUTHORS)
            }

        _lists.value = events.filter { it.kind == Kinds.FOLLOW_SET }
            .groupBy { it.dTag }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }
            .mapNotNull { event ->
                val coordinate = event.coordinate() ?: return@mapNotNull null
                val people = event.tagValues("p")
                    .filter { it.length == 64 }
                    .distinct()
                    .take(com.athena.reader.platform.Limits.MAX_FOLLOW_AUTHORS)
                if (people.isEmpty()) return@mapNotNull null
                PeopleList(
                    coordinate = coordinate,
                    title = event.tag("title") ?: event.tag("name") ?: coordinate.identifier,
                    pubkeys = people,
                )
            }

        Log.d(TAG, "follows: ${_follows.value.size}, lists: ${_lists.value.size}")
        refreshAcquaintances()
    }

    private suspend fun refreshAcquaintances() {
        val self = signerManager.current.value.pubkeyHex ?: return
        val follows = _follows.value
        if (follows.isEmpty()) {
            _acquaintances.value = emptyList()
            return
        }
        val hop = follows.take(60)
        val theirContacts = relayPool.fetch(
            listOf(Filter(authors = hop, kinds = listOf(Kinds.CONTACTS), limit = hop.size)),
        )
        val followingOfFollows = theirContacts
            .filter { it.kind == Kinds.CONTACTS }
            .groupBy { it.pubkey }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }
            .flatMap { it.tagValues("p") }

        val followerLists = relayPool.fetch(
            listOf(
                Filter(
                    kinds = listOf(Kinds.CONTACTS),
                    tags = mapOf("p" to hop.take(12)),
                    limit = 80,
                ),
            ),
        )
        val followersOfFollows = followerLists.filter { it.kind == Kinds.CONTACTS }.map { it.pubkey }

        _acquaintances.value = mergeAcquaintances(
            self = self,
            follows = follows.toSet(),
            followingOfFollows = followingOfFollows,
            followersOfFollows = followersOfFollows,
        )
        Log.d(TAG, "acquaintances: ${_acquaintances.value.size}")
    }

    fun clear() {
        _follows.value = emptyList()
        _lists.value = emptyList()
        _acquaintances.value = emptyList()
    }

    private companion object {
        const val TAG = "FollowRepository"
    }
}

internal fun mergeAcquaintances(
    self: String,
    follows: Set<String>,
    followingOfFollows: List<String>,
    followersOfFollows: List<String>,
    cap: Int = com.athena.reader.platform.Limits.MAX_FOLLOW_AUTHORS,
): List<String> {
    val out = LinkedHashSet<String>()
    followingOfFollows.forEach { key ->
        if (key.length == 64 && key != self && key !in follows) out.add(key)
    }
    followersOfFollows.forEach { key ->
        if (key.length == 64 && key != self && key !in follows) out.add(key)
    }
    return out.take(cap)
}
