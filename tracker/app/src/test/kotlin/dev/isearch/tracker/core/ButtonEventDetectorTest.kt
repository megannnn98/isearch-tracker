package dev.isearch.tracker.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonEventDetectorTest {
    private val detector = ButtonEventDetector()

    @Test
    fun singlePressIsNotDouble() {
        assertFalse(detector.onPress(1_000))
    }

    @Test
    fun twoPressesInsideWindowAreDouble() {
        detector.onPress(1_000)
        assertTrue(detector.onPress(1_400))
    }

    @Test
    fun twoPressesOutsideWindowAreNotDouble() {
        detector.onPress(1_000)
        assertFalse(detector.onPress(2_000))
        assertFalse(detector.onPress(3_500))
    }

    @Test
    fun boundaryIntervals() {
        detector.onPress(0)
        assertFalse(detector.onPress(1_000))
        detector.reset()
        detector.onPress(0)
        assertTrue(detector.onPress(999))
    }

    @Test
    fun duplicatePacketWithinMinIntervalIsIgnored() {
        detector.onPress(1_000)
        assertFalse(detector.onPress(1_005))
        assertFalse(detector.onPress(1_010))
        assertTrue(detector.onPress(1_300))
    }

    @Test
    fun thirdPressStartsNewSequence() {
        detector.onPress(1_000)
        assertTrue(detector.onPress(1_300))
        assertFalse(detector.onPress(1_600))
        assertTrue(detector.onPress(1_900))
    }

    @Test
    fun clockGoingBackwardsDoesNotTrigger() {
        detector.onPress(5_000)
        assertFalse(detector.onPress(4_000))
        assertTrue(detector.onPress(4_300))
    }

    @Test
    fun resetClearsPendingPress() {
        detector.onPress(1_000)
        detector.reset()
        assertFalse(detector.onPress(1_300))
    }
}
