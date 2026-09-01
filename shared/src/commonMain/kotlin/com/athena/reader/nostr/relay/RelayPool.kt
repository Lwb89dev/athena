package com.athena.reader.nostr.relay

import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.platform.Limits
import com.athena.reader.platform.Log
import com.athena.reader.platform.ioDispatcher
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** What a subscription reports back to the caller. */
sealed interface SubscriptionUpdate {
    data class Event(val event: NostrEvent, val relayUrl: String) : SubscriptionUpdate

    /** Every relay has finished sending stored events — the "loaded" moment. */
    data object EndOfStoredEvents : SubscriptionUpdate
}

/**
 * Fans a subscription out over every connected relay and merges the results,
 * dropping duplicates and anything whose signature does not check out.
 *
 * Verifying here rather than in the UI is deliberate: a relay is an untrusted
 * mirror, and this is the one place every inbound event has to pass through.
 */
class RelayPool(
    private val client: HttpClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Mutex()
    private val connections = linkedMapOf<String, RelayConnection>()

    private val _relayUrls = MutableStateFlow<List<String>>(emptyList())
    val relayUrls: StateFlow<List<String>> = _relayUrls

    suspend fun setRelays(urls: List<String>) {
        val wanted = urls.mapNotNull(::parseRelayUrl).distinct().take(Limits.MAX_RELAYS)
        val live = lock.withLock {
            connections.keys.filterNot(wanted::contains).forEach { removed ->
                connections.remove(removed)?.disconnect()
            }
            wanted.forEach { url -> connections.getOrPut(url) { RelayConnection(url, client, json, scope) } }
            connections.values.toList()
        }
        _relayUrls.value = wanted
        live.forEach(RelayConnection::connect)
    }

    /**
     * Opens a REQ on every relay. The flow stays open, streaming new events,
     * until the collector cancels — which is also what closes the REQ.
     *
     * [onReady] runs once the REQs have gone out. Request/response protocols
     * over relays (NIP-46) need it: this flow is *cold*, so publishing a request
     * before collection starts would send it before any subscription exists.
     */
    fun subscribe(
        filters: List<Filter>,
        onReady: suspend () -> Unit = {},
    ): Flow<SubscriptionUpdate> = callbackFlow {
        val subscriptionId = randomSubscriptionId()
        val relays = lock.withLock { connections.values.toList() }
        val seenEventIds = HashSet<String>()
        val pendingEose = relays.mapTo(mutableSetOf(), RelayConnection::url)

        relays.forEach { relay ->
            relay.messages
                .onEach { message -> handle(message, subscriptionId, relay, seenEventIds, pendingEose) }
                .launchIn(this)
            relay.subscribe(subscriptionId, filters)
        }
        if (relays.isEmpty()) send(SubscriptionUpdate.EndOfStoredEvents)
        onReady()

        awaitClose { relays.forEach { it.unsubscribe(subscriptionId) } }
    }

    private suspend fun ProducerScope<SubscriptionUpdate>.handle(
        message: RelayMessage,
        subscriptionId: String,
        relay: RelayConnection,
        seenEventIds: MutableSet<String>,
        pendingEose: MutableSet<String>,
    ) {
        when (message) {
            is RelayMessage.Event -> {
                if (message.subscriptionId != subscriptionId) return
                if (!seenEventIds.add(message.event.id)) return
                if (!message.event.verify()) {
                    Log.w(TAG, "dropping unverifiable event ${message.event.id} from ${relay.url}")
                    return
                }
                send(SubscriptionUpdate.Event(message.event, relay.url))
            }

            is RelayMessage.EndOfStoredEvents -> {
                if (message.subscriptionId != subscriptionId) return
                pendingEose.remove(relay.url)
                if (pendingEose.isEmpty()) send(SubscriptionUpdate.EndOfStoredEvents)
            }

            else -> Unit
        }
    }

    /**
     * Stored events only: returns as soon as every relay has sent EOSE, or when
     * [timeoutMillis] elapses — whichever comes first. A dead relay in the list
     * must not be able to hang a screen.
     */
    suspend fun fetch(filters: List<Filter>, timeoutMillis: Long = 8_000): List<NostrEvent> {
        val collected = mutableListOf<NostrEvent>()
        withTimeoutOrNull(timeoutMillis) {
            subscribe(filters)
                .takeWhile { update ->
                    update !is SubscriptionUpdate.EndOfStoredEvents &&
                        collected.size < Limits.MAX_FETCH_EVENTS
                }
                .collect { update ->
                    if (update is SubscriptionUpdate.Event) collected += update.event
                }
        }
        return collected
    }

    fun publish(event: NostrEvent) {
        scope.launch {
            lock.withLock { connections.values.toList() }.forEach { it.publish(event) }
        }
    }

    private fun randomSubscriptionId(): String =
        (1..16).map { HEX[Random.nextInt(HEX.length)] }.joinToString("")

    private companion object {
        const val TAG = "RelayPool"
        const val HEX = "0123456789abcdef"
    }
}
