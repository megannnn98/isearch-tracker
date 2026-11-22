package dev.isearch.tracker.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleProtocolTest {
    @Test
    fun uuidsMatchOriginalApp() {
        assertEquals("0000ffe0-0000-1000-8000-00805f9b34fb", BleProtocol.SERVICE_SCAN_FILTER.toString())
        assertEquals("0000ffe1-0000-1000-8000-00805f9b34fb", BleProtocol.BUTTON_CHARACTERISTIC.toString())
        assertEquals("0000ffe2-0000-1000-8000-00805f9b34fb", BleProtocol.DISCONNECT_ALARM_CHARACTERISTIC.toString())
        assertEquals("00002a06-0000-1000-8000-00805f9b34fb", BleProtocol.ALERT_LEVEL_CHARACTERISTIC.toString())
        assertEquals("00001802-0000-1000-8000-00805f9b34fb", BleProtocol.IMMEDIATE_ALERT_SERVICE.toString())
        assertEquals("00002a19-0000-1000-8000-00805f9b34fb", BleProtocol.BATTERY_LEVEL_CHARACTERISTIC.toString())
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", BleProtocol.CCCD.toString())
    }

    @Test
    fun firstByteOneIsButtonPress() {
        assertEquals(BleProtocol.Message.ButtonPress, BleProtocol.parseButtonNotification(byteArrayOf(1)))
        assertEquals(BleProtocol.Message.ButtonPress, BleProtocol.parseButtonNotification(byteArrayOf(1, 7, 9)))
    }

    @Test
    fun otherPacketsAreUnknown() {
        val zero = BleProtocol.parseButtonNotification(byteArrayOf(0))
        assertTrue(zero is BleProtocol.Message.Unknown)
        assertTrue(BleProtocol.parseButtonNotification(byteArrayOf(2, 1)) is BleProtocol.Message.Unknown)
    }

    @Test
    fun emptyAndNullAreUnknown() {
        assertTrue(BleProtocol.parseButtonNotification(null) is BleProtocol.Message.Unknown)
        assertTrue(BleProtocol.parseButtonNotification(ByteArray(0)) is BleProtocol.Message.Unknown)
    }

    @Test
    fun alertCommands() {
        assertArrayEquals(byteArrayOf(1), BleProtocol.ALERT_ON)
        assertArrayEquals(byteArrayOf(0), BleProtocol.ALERT_OFF)
    }
}
