package com.athena.reader.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcquaintancesTest {

    private val self = "aa".repeat(32)
    private val friend = "bb".repeat(32)
    private val hop = "cc".repeat(32)
    private val follower = "dd".repeat(32)

    @Test
    fun `drops self, direct follows, and malformed keys`() {
        val result = mergeAcquaintances(
            self = self,
            follows = setOf(friend),
            followingOfFollows = listOf(self, friend, hop, "short"),
            followersOfFollows = listOf(follower, friend),
        )
        assertEquals(listOf(hop, follower), result)
    }

    @Test
    fun `caps the neighbourhood`() {
        val crowd = (1..50).map { index -> index.toString().padStart(64, '0') }
        val result = mergeAcquaintances(
            self = self,
            follows = emptySet(),
            followingOfFollows = crowd,
            followersOfFollows = emptyList(),
            cap = 10,
        )
        assertEquals(10, result.size)
        assertTrue(result.all { it.length == 64 })
    }
}
