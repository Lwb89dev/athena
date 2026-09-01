package com.athena.reader.nostr.crypto

import com.athena.reader.platform.Log
import fr.acinq.secp256k1.Secp256k1

/**
 * BIP-340 signature verification. Athena never holds a private key — signing
 * always goes out to Amber or a bunker — so this side is verify-only on purpose.
 */
object Schnorr {

    fun verify(eventId: String, pubkeyHex: String, signatureHex: String): Boolean {
        if (eventId.length != 64 || pubkeyHex.length != 64 || signatureHex.length != 128) return false
        return runCatching {
            Secp256k1.verifySchnorr(signatureHex.hexToBytes(), eventId.hexToBytes(), pubkeyHex.hexToBytes())
        }.getOrElse { error ->
            Log.w(TAG, "schnorr verification threw for event $eventId", error)
            false
        }
    }

    private const val TAG = "Schnorr"
}
