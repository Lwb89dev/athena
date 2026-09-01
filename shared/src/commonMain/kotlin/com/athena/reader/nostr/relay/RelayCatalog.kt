package com.athena.reader.nostr.relay

/**
 * Public relays offered during onboarding. Eight well-run endpoints, not a
 * ranking of the whole network — relays come and go, and the user can add
 * their own URL on the same screen.
 *
 * `relay.nostr.band` is listed but unchecked: it is excellent for discovery
 * and it archives everything, including encrypted sync snapshots. The user
 * has to opt in.
 */
data class SuggestedRelay(
    val url: String,
    val name: String,
    val blurb: String,
    val selectedByDefault: Boolean = false,
)

object RelayCatalog {
    val onboarding: List<SuggestedRelay> = listOf(
        SuggestedRelay(
            url = "wss://relay.damus.io",
            name = "Damus",
            blurb = "Large public relay, high reach",
            selectedByDefault = true,
        ),
        SuggestedRelay(
            url = "wss://nos.lol",
            name = "nos.lol",
            blurb = "Stable community relay",
            selectedByDefault = true,
        ),
        SuggestedRelay(
            url = "wss://thecitadel.nostr1.com",
            name = "The Citadel",
            blurb = "NKBIP-01 publications",
            selectedByDefault = true,
        ),
        SuggestedRelay(
            url = "wss://nostr.wine",
            name = "nostr.wine",
            blurb = "Filtered public relay",
            selectedByDefault = true,
        ),
        SuggestedRelay(
            url = "wss://relay.primal.net",
            name = "Primal",
            blurb = "Primal's public relay",
        ),
        SuggestedRelay(
            url = "wss://relay.snort.social",
            name = "Snort",
            blurb = "Fast European relay",
        ),
        SuggestedRelay(
            url = "wss://relay.nostr.band",
            name = "nostr.band",
            blurb = "Discovery; archives everything",
        ),
        SuggestedRelay(
            url = "wss://offchain.pub",
            name = "offchain.pub",
            blurb = "General-purpose public relay",
        ),
    )

    val defaultUrls: Set<String>
        get() = onboarding.filter { it.selectedByDefault }.map { it.url }.toSet()
}
