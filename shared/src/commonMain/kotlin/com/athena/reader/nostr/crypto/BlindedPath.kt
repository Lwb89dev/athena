package com.athena.reader.nostr.crypto

/**
 * Blinded `d` tags for NIP-78 sync slots.
 *
 * A `d` tag is indexed and queryable: `REQ {"#d": [...]}` returns every event
 * carrying it. So a readable tag like `athena:progress:30040:<author>:<book>`
 * tells any relay exactly which book a given npub is reading — and lets it list
 * every reader of a given book. The content being encrypted does not help; the
 * *address* is the leak.
 *
 * Here the tag is `HMAC-SHA256(secret, "namespace:value")`, where `secret` is a
 * random 32 bytes known only to the user's devices. To a relay every slot is an
 * unlinkable 64-character hex string: it cannot tell which book a slot refers
 * to, cannot group two slots as belonging to the same book across users, and
 * cannot check whether a specific book appears in a specific user's slots.
 *
 * HMAC rather than a bare hash because the input space is small and public:
 * book coordinates are enumerable, so `sha256(coordinate)` would be a rainbow
 * table. Keying it makes the mapping uncomputable without the secret.
 */
object BlindedPath {

    /** Reading position of one book. */
    fun progress(secret: ByteArray, bookCoordinate: String): String =
        derive(secret, NAMESPACE_PROGRESS, bookCoordinate)

    /** The single slot holding every private favourite. */
    fun privateFavorites(secret: ByteArray): String =
        derive(secret, NAMESPACE_FAVORITES, "")

    /** The single slot holding every private highlight. */
    fun privateHighlights(secret: ByteArray): String =
        derive(secret, NAMESPACE_HIGHLIGHTS, "")

    fun derive(secret: ByteArray, namespace: String, value: String): String {
        require(secret.size == SECRET_SIZE) {
            "a blinding secret is $SECRET_SIZE bytes, was ${secret.size}"
        }
        return Hmac.sha256(secret, "$namespace:$value".encodeToByteArray()).toHex()
    }

    /**
     * The one slot that cannot be blinded: it is where the secret itself is
     * fetched from, so its address must be derivable from public information
     * alone. Derived from the pubkey so a single query cannot enumerate every
     * user of the app — but a targeted "does this npub use Athena?" check
     * remains computable, and that is the honest ceiling of this design.
     */
    fun bootstrapSlot(pubkeyHex: String): String =
        com.athena.reader.platform.sha256(
            "$BOOTSTRAP_NAMESPACE:$pubkeyHex".encodeToByteArray(),
        ).toHex()

    const val SECRET_SIZE = 32

    private const val NAMESPACE_PROGRESS = "progress"
    private const val NAMESPACE_FAVORITES = "favorites"
    private const val NAMESPACE_HIGHLIGHTS = "highlights"
    private const val BOOTSTRAP_NAMESPACE = "project-athena-v1"
}
