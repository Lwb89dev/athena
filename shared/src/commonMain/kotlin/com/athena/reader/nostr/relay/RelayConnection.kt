package com.athena.reader.nostr.relay

import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.platform.Limits
import com.athena.reader.platform.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

enum class RelayStatus { Disconnected, Connecting, Connected, Failed }

/**
 * One websocket to one relay.
 *
 * It reconnects with backoff and replays the subscriptions it was holding, so
 * callers can treat a relay as always-on and stop caring about the socket.
 */
class RelayConnection(
    val url: String,
    private val client: HttpClient,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    private val _messages = MutableSharedFlow<RelayMessage>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<RelayMessage> = _messages

    private val _status = MutableStateFlow(RelayStatus.Disconnected)
    val status: StateFlow<RelayStatus> = _status

    private val lock = Mutex()
    private val activeSubscriptions = mutableMapOf<String, List<Filter>>()

    /**
     * Events signed while the socket was down.
     *
     * Subscriptions are rebuilt on reconnect from [activeSubscriptions], but an
     * EVENT is a one-shot: dropping it silently loses a highlight the user just
     * made. Bounded, because a relay that never comes back must not grow this
     * without limit — the local row survives either way and can be retried.
     */
    private val outbox = ArrayDeque<NostrEvent>()
    private var session: DefaultClientWebSocketSession? = null
    private var pump: Job? = null
    private var failureCount = 0

    fun connect() {
        if (pump?.isActive == true) return
        pump = scope.launch { runConnection() }
    }

    fun subscribe(subscriptionId: String, filters: List<Filter>) {
        scope.launch {
            lock.withLock { activeSubscriptions[subscriptionId] = filters }
            connect()
            send(reqMessage(subscriptionId, filters))
        }
    }

    fun unsubscribe(subscriptionId: String) {
        scope.launch {
            lock.withLock { activeSubscriptions.remove(subscriptionId) }
            send(buildJsonArray { add("CLOSE"); add(subscriptionId) }.toString())
        }
    }

    fun publish(event: NostrEvent) {
        scope.launch {
            connect()
            val open = session
            if (open == null) {
                enqueue(event)
                return@launch
            }
            runCatching { open.send(eventMessage(event)) }
                .onFailure {
                    Log.d(TAG, "publish failed on $url, queued for reconnect")
                    enqueue(event)
                }
        }
    }

    private suspend fun enqueue(event: NostrEvent) {
        lock.withLock {
            while (outbox.size >= OUTBOX_LIMIT) outbox.removeFirst()
            outbox.addLast(event)
        }
    }

    fun disconnect() {
        scope.launch {
            lock.withLock { activeSubscriptions.clear() }
            session?.close()
            session = null
            pump?.cancel()
            pump = null
            _status.value = RelayStatus.Disconnected
        }
    }

    /** Connect, replay subscriptions, pump frames until the socket dies. */
    private suspend fun runConnection() {
        while (scope.isActive) {
            _status.value = RelayStatus.Connecting
            val opened = runCatching { client.webSocketSession(url) }.getOrElse { error ->
                Log.w(TAG, "$url did not open: ${error.message}")
                null
            }

            if (opened == null) {
                if (!backOff()) return
                continue
            }
            pumpFrames(opened)
            if (!backOff()) return
        }
    }

    private suspend fun pumpFrames(opened: DefaultClientWebSocketSession) {
        session = opened
        failureCount = 0
        _status.value = RelayStatus.Connected
        replaySubscriptions(opened)
        flushOutbox(opened)

        runCatching {
            for (frame in opened.incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                if (text.length > Limits.MAX_WS_FRAME_BYTES) continue
                RelayMessage.parse(json, text)?.let(_messages::tryEmit)
            }
        }.onFailure { Log.d(TAG, "$url dropped: ${it.message}") }

        session = null
        _status.value = RelayStatus.Disconnected
    }

    private suspend fun replaySubscriptions(opened: DefaultClientWebSocketSession) {
        val pending = lock.withLock { activeSubscriptions.toMap() }
        pending.forEach { (id, filters) -> opened.send(reqMessage(id, filters)) }
    }

    private suspend fun flushOutbox(opened: DefaultClientWebSocketSession) {
        val pending = lock.withLock { outbox.toList().also { outbox.clear() } }
        pending.forEach { event ->
            runCatching { opened.send(eventMessage(event)) }
                .onFailure { Log.d(TAG, "outbox flush failed on $url") }
        }
        if (pending.isNotEmpty()) Log.d(TAG, "flushed ${pending.size} queued events to $url")
    }

    private fun eventMessage(event: NostrEvent): String = buildJsonArray {
        add("EVENT")
        add(json.encodeToJsonElement(NostrEvent.serializer(), event))
    }.toString()

    /** Returns false when there is nothing left worth reconnecting for. */
    private suspend fun backOff(): Boolean {
        val hasWork = lock.withLock { activeSubscriptions.isNotEmpty() || outbox.isNotEmpty() }
        if (!hasWork) {
            _status.value = RelayStatus.Disconnected
            return false
        }
        _status.value = RelayStatus.Failed
        delay(BACKOFF_MILLIS[failureCount.coerceAtMost(BACKOFF_MILLIS.lastIndex)])
        failureCount++
        return true
    }

    /**
     * Sends if the socket is up. When it is not, the message is dropped on
     * purpose rather than queued: every REQ we care about lives in
     * [activeSubscriptions] and is replayed by [replaySubscriptions] the moment
     * the socket opens, so queueing here would only send it twice.
     */
    private suspend fun send(payload: String) {
        val open = session ?: return
        runCatching { open.send(payload) }.onFailure { Log.d(TAG, "send failed on $url") }
    }

    private fun reqMessage(subscriptionId: String, filters: List<Filter>): String =
        buildJsonArray {
            add("REQ")
            add(subscriptionId)
            filters.forEach { add(it.toJson()) }
        }.toString()

    private companion object {
        const val TAG = "RelayConnection"
        /** A long EPUB becomes one event per chapter; 64 dropped the tail. */
        const val OUTBOX_LIMIT = 512
        val BACKOFF_MILLIS = longArrayOf(1_000, 2_000, 5_000, 15_000, 30_000, 60_000)
    }
}
