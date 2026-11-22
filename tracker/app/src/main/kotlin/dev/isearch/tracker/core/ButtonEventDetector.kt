package dev.isearch.tracker.core

/**
 * Detects a double press from timestamps of single presses.
 *
 * Window taken from the original app (MediaPlayerTools.OnFDQClick): 10 ms < dt < 1000 ms.
 * Intervals <= [MIN_INTERVAL_MS] are treated as a duplicated BLE packet and ignored.
 * After a double press the state is cleared, so a third press starts a new sequence.
 */
class ButtonEventDetector(
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val maxIntervalMs: Long = MAX_INTERVAL_MS,
) {
    private var lastPressMs: Long? = null

    /** Returns true when this press completes a double press. [nowMs] must be monotonic. */
    fun onPress(nowMs: Long): Boolean {
        val last = lastPressMs
        if (last == null) {
            lastPressMs = nowMs
            return false
        }
        val delta = nowMs - last
        if (delta < 0) {
            lastPressMs = nowMs
            return false
        }
        if (delta <= minIntervalMs) {
            return false
        }
        if (delta < maxIntervalMs) {
            lastPressMs = null
            return true
        }
        lastPressMs = nowMs
        return false
    }

    fun reset() {
        lastPressMs = null
    }

    companion object {
        const val MIN_INTERVAL_MS = 10L
        const val MAX_INTERVAL_MS = 1000L
    }
}
