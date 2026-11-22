package dev.isearch.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun delayDoublesAndIsCapped() {
        val policy = ReconnectPolicy(baseDelayMs = 1_000, maxDelayMs = 8_000)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L), List(5) { policy.onFailure() })
        assertEquals(5, policy.attempts)
    }

    @Test
    fun successResetsSequence() {
        val policy = ReconnectPolicy(1_000, 30_000)
        policy.onFailure()
        policy.onFailure()
        policy.onSuccess()
        assertEquals(0, policy.attempts)
        assertEquals(1_000L, policy.onFailure())
    }

    @Test
    fun manyFailuresDoNotOverflow() {
        val policy = ReconnectPolicy(1_000, 30_000)
        repeat(200) { policy.onFailure() }
        assertEquals(30_000L, policy.onFailure())
    }

    @Test
    fun invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(100, 10) }
    }
}
