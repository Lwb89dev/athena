package com.athena.reader.nostr.signer

import com.athena.reader.nostr.crypto.EventId
import com.athena.reader.nostr.crypto.KeyPair
import com.athena.reader.nostr.crypto.Nip44
import com.athena.reader.nostr.crypto.hexToBytes
import com.athena.reader.nostr.crypto.signEvent
import com.athena.reader.nostr.crypto.toHex
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.relay.SubscriptionUpdate
import com.athena.reader.platform.Log
import com.athena.reader.platform.nowSeconds
import com.athena.reader.platform.randomBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * NIP-46 remote signing over relays ("bunker").
 *
 * The shape of it: we hold a throwaway *session* keypair — not the user's key —
 * and use it to exchange NIP-44 encrypted JSON-RPC messages with the remote
 * signer as kind 24133 events. The signer holds the real key and hands back
 * signatures. This is how the desktop builds log in, where no Amber exists.
 *
 * Two details that are easy to get wrong and are handled here:
 *
 *  - the response subscription is opened *before* the request is published,
 *    because a fast signer can answer before a late subscription is live;
 *  - a signer may reply `auth_url`, meaning "send your user to this page first".
 *    That is surfaced through [authUrl] rather than treated as an error.
 */
class Nip46Signer(
    private val bunker: BunkerUri,
    private val sessionKey: KeyPair,
    private val relayPool: RelayPool,
    private val json: Json,
    knownPubkey: String? = null,
) : NostrSigner {

    private val conversationKey by lazy {
        Nip44.conversationKey(sessionKey.privateKey, bunker.remoteSignerPubkey.hexToBytes())
    }
    private val requestLock = Mutex()

    private var userPubkey: String? = knownPubkey

    private val _authUrl = MutableStateFlow<String?>(null)

    /** Non-null when the signer wants the user to visit a page to approve us. */
    val authUrl: StateFlow<String?> = _authUrl

    override val pubkeyHex: String? get() = userPubkey

    /** Opens the session: `connect`, then ask who we are signing as. */
    suspend fun connect(): Result<String> {
        relayPool.setRelays(bunker.relays)

        val params = buildList {
            add(bunker.remoteSignerPubkey)
            add(bunker.secret.orEmpty())
            add(REQUESTED_PERMISSIONS)
        }
        call("connect", params).getOrElse { return Result.failure(it) }
        return requestPublicKey()
    }

    override suspend fun requestPublicKey(): Result<String> {
        relayPool.setRelays(bunker.relays)
        val result = call("get_public_key", emptyList()).getOrElse { return Result.failure(it) }
        if (result.length != 64) {
            return Result.failure(SignerProtocolError("bunker returned a malformed pubkey"))
        }
        userPubkey = result
        return Result.success(result)
    }

    override suspend fun sign(unsigned: UnsignedEvent): Result<NostrEvent> {
        val pubkey = userPubkey ?: return Result.failure(NostrSigner.NotLoggedIn)
        val payload = unsignedToJson(pubkey, unsigned)

        val result = call("sign_event", listOf(payload)).getOrElse { return Result.failure(it) }
        val event = parseSignedEvent(result, pubkey, unsigned)
            ?: return Result.failure(SignerProtocolError("bunker returned an unusable sign_event result"))

        if (!event.verify()) {
            return Result.failure(SignerProtocolError("bunker returned an invalid signature"))
        }
        return Result.success(event)
    }

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): Result<String> =
        call("nip44_encrypt", listOf(peerPubkeyHex, plaintext))

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): Result<String> =
        call("nip44_decrypt", listOf(peerPubkeyHex, ciphertext))

    /** Round-trip one JSON-RPC call. Serialised: bunkers answer one at a time. */
    private suspend fun call(method: String, params: List<String>): Result<String> =
        requestLock.withLock {
            val requestId = randomBytes(8).toHex()
            val request = buildJsonObject {
                put("id", requestId)
                put("method", method)
                put("params", buildJsonArray { params.forEach { add(it) } })
            }

            val envelope = envelopeFor(request.toString())
                ?: return Result.failure(
                    SignerProtocolError(
                        "could not encrypt the $method request — the payload may be too large " +
                            "for NIP-44 (65 KiB). Try a shorter chapter.",
                    ),
                )

            // The request goes out only once the REQ has been sent: `subscribe`
            // returns a *cold* flow, so publishing before collection begins would
            // put the request on the wire with no subscription listening for the
            // answer. The `since` slack in responseFilter is the second line of
            // defence, catching a reply that still beats the REQ to the relay.
            val responses = relayPool.subscribe(listOf(responseFilter())) {
                relayPool.publish(envelope)
            }

            awaitResult(responses, requestId, method)
        }

    private suspend fun awaitResult(
        responses: Flow<SubscriptionUpdate>,
        requestId: String,
        method: String,
    ): Result<String> {
        val outcome = withTimeoutOrNull(RESPONSE_TIMEOUT_MILLIS) {
            responses
                .filterIsInstance<SubscriptionUpdate.Event>()
                .first { update -> matches(update.event, requestId) != null }
                .let { update -> matches(update.event, requestId) }
        }
        return when (outcome) {
            null -> Result.failure(SignerProtocolError("the bunker did not answer $method in time"))
            is RpcOutcome.Success -> Result.success(outcome.result)
            is RpcOutcome.AuthRequired -> {
                _authUrl.value = outcome.url
                Result.failure(BunkerAuthRequired(outcome.url))
            }

            is RpcOutcome.Failure -> Result.failure(SignerProtocolError(outcome.message))
        }
    }

    private sealed interface RpcOutcome {
        data class Success(val result: String) : RpcOutcome
        data class AuthRequired(val url: String) : RpcOutcome
        data class Failure(val message: String) : RpcOutcome
    }

    /** Decrypts a candidate response and reports it only if the id matches. */
    private fun matches(event: NostrEvent, requestId: String): RpcOutcome? {
        if (event.kind != Kinds.NOSTR_CONNECT) return null
        if (event.pubkey != bunker.remoteSignerPubkey) return null

        val plaintext = runCatching { Nip44.decrypt(event.content, conversationKey) }.getOrElse {
            Log.d(TAG, "could not decrypt a 24133 event: ${it.message}")
            return null
        }
        val body = runCatching { json.parseToJsonElement(plaintext) as? JsonObject }.getOrNull()
            ?: return null
        if (body.string("id") != requestId) return null

        val result = body.string("result")
        val error = body.string("error")

        return when {
            // `auth_url` is not a failure: the signer wants the user to approve us.
            result == "auth_url" -> RpcOutcome.AuthRequired(error.orEmpty())
            !error.isNullOrBlank() -> RpcOutcome.Failure(error)
            result != null -> RpcOutcome.Success(result)
            else -> RpcOutcome.Failure("empty response from the bunker")
        }
    }

    private fun envelopeFor(requestJson: String): NostrEvent? = runCatching {
        sessionKey.signEvent(
            UnsignedEvent(
                kind = Kinds.NOSTR_CONNECT,
                tags = listOf(listOf("p", bunker.remoteSignerPubkey)),
                content = Nip44.encrypt(requestJson, conversationKey),
            ),
        )
    }.getOrNull()

    private fun responseFilter() = Filter(
        authors = listOf(bunker.remoteSignerPubkey),
        kinds = listOf(Kinds.NOSTR_CONNECT),
        tags = mapOf("p" to listOf(sessionKey.publicKeyHex)),
        // A little slack: signer clocks are not ours, and a strict `now` drops replies.
        since = nowSeconds() - CLOCK_SLACK_SECONDS,
    )

    /** Signers may return the whole signed event, or just the signature. */
    private fun parseSignedEvent(
        result: String,
        pubkey: String,
        unsigned: UnsignedEvent,
    ): NostrEvent? {
        runCatching { json.decodeFromString(NostrEvent.serializer(), result) }
            .onSuccess { return it }

        if (result.length != 128) return null
        return NostrEvent(
            id = EventId.compute(pubkey, unsigned),
            pubkey = pubkey,
            createdAt = unsigned.createdAt,
            kind = unsigned.kind,
            tags = unsigned.tags,
            content = unsigned.content,
            sig = result,
        )
    }

    private fun unsignedToJson(pubkey: String, unsigned: UnsignedEvent): String = buildJsonObject {
        put("id", EventId.compute(pubkey, unsigned))
        put("pubkey", pubkey)
        put("created_at", unsigned.createdAt)
        put("kind", unsigned.kind)
        put("tags", buildJsonArray {
            unsigned.tags.forEach { tag -> add(buildJsonArray { tag.forEach { add(it) } }) }
        })
        put("content", unsigned.content)
    }.toString()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val TAG = "Nip46Signer"
        const val RESPONSE_TIMEOUT_MILLIS = 60_000L
        const val CLOCK_SLACK_SECONDS = 60L

        /**
         * Asked for once at connect, so later writes do not each need approval.
         * `sign_event` with no kind is the blanket some bunkers want; the
         * numbered ones cover Amber-style per-kind grants. 30040/30041 are the
         * book index and chapters — without them, publishing an EPUB hangs on
         * the first unsigned kind waiting for a prompt that never appears.
         */
        const val REQUESTED_PERMISSIONS =
            "sign_event," +
                "sign_event:5,sign_event:9802,sign_event:10002," +
                "sign_event:30003,sign_event:30023,sign_event:30040,sign_event:30041,sign_event:30078," +
                "nip44_encrypt,nip44_decrypt,get_public_key"
    }
}

/** The signer wants the user to visit [url] before it will act for us. */
class BunkerAuthRequired(val url: String) :
    IllegalStateException("The bunker needs approval at $url")
