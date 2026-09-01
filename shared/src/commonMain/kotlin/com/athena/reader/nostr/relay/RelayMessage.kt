package com.athena.reader.nostr.relay

import com.athena.reader.nostr.model.NostrEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Messages a relay can send us (NIP-01, NIP-42). */
sealed interface RelayMessage {
    data class Event(val subscriptionId: String, val event: NostrEvent) : RelayMessage
    data class EndOfStoredEvents(val subscriptionId: String) : RelayMessage
    data class Closed(val subscriptionId: String, val reason: String) : RelayMessage
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage
    data class Notice(val message: String) : RelayMessage
    data class AuthChallenge(val challenge: String) : RelayMessage

    companion object {
        fun parse(json: Json, raw: String): RelayMessage? {
            val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return null
            return when (array.str(0)) {
                "EVENT" -> array.asEvent(json)
                "EOSE" -> array.str(1)?.let(::EndOfStoredEvents)
                "CLOSED" -> array.str(1)?.let { Closed(it, array.str(2).orEmpty()) }
                "OK" -> array.str(1)?.let { Ok(it, array.str(2) == "true", array.str(3).orEmpty()) }
                "NOTICE" -> array.str(1)?.let(::Notice)
                "AUTH" -> array.str(1)?.let(::AuthChallenge)
                else -> null
            }
        }

        private fun JsonArray.asEvent(json: Json): Event? {
            val subscriptionId = str(1) ?: return null
            val element = getOrNull(2) ?: return null
            val event = runCatching {
                json.decodeFromJsonElement(NostrEvent.serializer(), element)
            }.getOrNull() ?: return null
            return Event(subscriptionId, event)
        }

        private fun JsonArray.str(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.content
    }
}
