package com.athena.reader.nostr.signer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.athena.reader.nostr.crypto.EventId
import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.nostr.model.UnsignedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * NIP-55 external signer (Amber and compatible apps).
 *
 * Two transports exist and we use both:
 *  - a foreground `nostrsigner:` intent, which shows Amber's approval screen;
 *  - a background ContentProvider query, which works silently once the user has
 *    granted the app a standing permission. Reading progress is written on every
 *    page turn, so it *must* take the silent path — we try the provider first and
 *    only fall back to the intent when it says "ask the user".
 */
class AmberSigner(
    private val context: Context,
    private val launcher: ExternalSignerLauncher,
    private val json: Json,
    private var knownPubkey: String? = null,
    private var signerApp: String = DEFAULT_SIGNER_PACKAGE,
) : NostrSigner, HasSignerPackage {

    override val pubkeyHex: String? get() = knownPubkey

    /** Which signer app answered at login; reused for every later call. */
    override val signerPackage: String get() = signerApp

    override suspend fun requestPublicKey(): Result<String> {
        // No explicit package here: let the user pick their signer if several are
        // installed. Whoever answers tells us its package for subsequent calls.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
            putExtra("type", TYPE_GET_PUBLIC_KEY)
            putExtra("permissions", defaultPermissions())
        }
        val result = launcher.request(intent) ?: return Result.failure(SignerDismissed)
        result.getStringExtra("package")?.takeIf { it.isNotBlank() }?.let { signerApp = it }

        val raw = result.getStringExtra("result")
            ?: result.getStringExtra("signature")
            ?: return Result.failure(SignerDismissed)

        val hex = if (raw.startsWith("npub")) Nip19.decodeNpub(raw) else raw
        if (hex == null || hex.length != 64) return Result.failure(SignerProtocolError("bad pubkey: $raw"))
        knownPubkey = hex
        return Result.success(hex)
    }

    override suspend fun sign(unsigned: UnsignedEvent): Result<NostrEvent> {
        val pubkey = knownPubkey ?: return Result.failure(NostrSigner.NotLoggedIn)
        val payload = unsignedToJson(pubkey, unsigned)

        val signed = queryProvider(PROVIDER_SIGN_EVENT, payload, pubkey, column = "event")
            ?: intentCall(TYPE_SIGN_EVENT, payload, pubkey, column = "event")
            ?: return Result.failure(SignerDismissed)

        return runCatching { json.decodeFromString(NostrEvent.serializer(), signed) }
            .mapCatching { event ->
                if (!event.verify()) throw SignerProtocolError("signer returned an invalid signature")
                event
            }
    }

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): Result<String> =
        crypt(PROVIDER_NIP44_ENCRYPT, TYPE_NIP44_ENCRYPT, plaintext, peerPubkeyHex)

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): Result<String> =
        crypt(PROVIDER_NIP44_DECRYPT, TYPE_NIP44_DECRYPT, ciphertext, peerPubkeyHex)

    private suspend fun crypt(
        provider: String,
        type: String,
        payload: String,
        peerPubkeyHex: String,
    ): Result<String> {
        val pubkey = knownPubkey ?: return Result.failure(NostrSigner.NotLoggedIn)
        val result = queryProvider(provider, payload, pubkey, column = "result", peer = peerPubkeyHex)
            ?: intentCall(type, payload, pubkey, column = "result", peer = peerPubkeyHex)
        return result?.let { Result.success(it) } ?: Result.failure(SignerDismissed)
    }

    /** Silent path: returns null when the provider is absent or defers to the UI. */
    private suspend fun queryProvider(
        providerPath: String,
        payload: String,
        currentUser: String,
        column: String,
        peer: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse("content://$signerApp.$providerPath")
        val args = if (peer == null) {
            arrayOf(payload, currentUser)
        } else {
            arrayOf(payload, peer, currentUser)
        }
        runCatching {
            context.contentResolver.query(uri, args, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                if (cursor.getColumnIndex("rejected") >= 0) return@use null
                val index = cursor.getColumnIndex(column).takeIf { it >= 0 }
                    ?: cursor.getColumnIndex("result").takeIf { it >= 0 }
                    ?: return@use null
                cursor.getString(index)
            }
        }.getOrNull()
    }

    /** Foreground path: shows Amber's approval sheet. */
    private suspend fun intentCall(
        type: String,
        payload: String,
        currentUser: String,
        column: String,
        peer: String? = null,
    ): String? {
        val intent = baseIntent(payload, type).apply {
            putExtra("current_user", currentUser)
            peer?.let { putExtra("pubKey", it) }
        }
        val result = launcher.request(intent) ?: return null
        return result.getStringExtra(column) ?: result.getStringExtra("result")
    }

    private fun baseIntent(payload: String, type: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$payload")).apply {
            `package` = signerApp
            putExtra("type", type)
        }

    /** The event JSON Amber expects: unsigned, but with `id` and `pubkey` filled in. */
    private fun unsignedToJson(pubkey: String, unsigned: UnsignedEvent): String =
        buildJsonObject {
            put("id", EventId.compute(pubkey, unsigned))
            put("pubkey", pubkey)
            put("created_at", unsigned.createdAt)
            put("kind", unsigned.kind)
            put("tags", buildJsonArray {
                unsigned.tags.forEach { tag -> add(buildJsonArray { tag.forEach { add(it) } }) }
            })
            put("content", unsigned.content)
        }.toString()

    /**
     * Asked for once at login so later writes can go through the silent provider
     * path instead of interrupting the reader.
     */
    private fun defaultPermissions(): String = buildJsonArray {
        SILENT_KINDS.forEach { kind ->
            addJsonObject {
                put("type", "sign_event")
                put("kind", kind)
            }
        }
        addJsonObject { put("type", "nip44_encrypt") }
        addJsonObject { put("type", "nip44_decrypt") }
    }.toString()

    companion object {
        const val DEFAULT_SIGNER_PACKAGE = "com.greenart7c3.nostrsigner"

        private const val TYPE_GET_PUBLIC_KEY = "get_public_key"
        private const val TYPE_SIGN_EVENT = "sign_event"
        private const val TYPE_NIP44_ENCRYPT = "nip44_encrypt"
        private const val TYPE_NIP44_DECRYPT = "nip44_decrypt"

        private const val PROVIDER_SIGN_EVENT = "SIGN_EVENT"
        private const val PROVIDER_NIP44_ENCRYPT = "NIP44_ENCRYPT"
        private const val PROVIDER_NIP44_DECRYPT = "NIP44_DECRYPT"

        /** Kinds we want pre-approved: highlights, favorites, reading progress. */
        private val SILENT_KINDS = listOf(9_802, 30_003, 30_078, 10_002)

        /** True when any NIP-55 signer is installed. */
        fun isAvailable(context: Context): Boolean {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
            return context.packageManager
                .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .isNotEmpty()
        }
    }
}
