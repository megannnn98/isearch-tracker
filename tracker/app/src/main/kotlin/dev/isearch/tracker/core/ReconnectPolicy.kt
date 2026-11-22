package dev.isearch.tracker.core

/**
 * Exponential backoff for reconnects: base, 2*base, 4*base ... capped at [maxDelayMs].
 * [onSuccess] resets the sequence.
 */
class ReconnectPolicy(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
) {
    init {
        require(baseDelayMs > 0 && maxDelayMs >= baseDelayMs)
    }

    var attempts: Int = 0
        private set

    /** Registers a failure and returns the delay before the next attempt. */
    fun onFailure(): Long {
        val shift = attempts.coerceAtMost(MAX_SHIFT)
        attempts++
        return (baseDelayMs shl shift).coerceAtMost(maxDelayMs)
    }

    fun onSuccess() {
        attempts = 0
    }

    private companion object {
        const val MAX_SHIFT = 20
    }
}
