package dev.isearch.tracker.core

import java.util.UUID

/**
 * Protocol of the fob as found in iSearching G 1.0.6 (see docs/REVERSE_ENGINEERING.md, section 3).
 * Only facts confirmed by the original code are encoded here.
 */
object BleProtocol {
    val SERVICE_SCAN_FILTER: UUID = uuid16(0xFFE0)
    val BUTTON_CHARACTERISTIC: UUID = uuid16(0xFFE1)
    val DISCONNECT_ALARM_CHARACTERISTIC: UUID = uuid16(0xFFE2)
    /** Immediate Alert service. 2A06 also exists in Link Loss (1803) on this fob; that one must not be used. */
    val IMMEDIATE_ALERT_SERVICE: UUID = uuid16(0x1802)
    val ALERT_LEVEL_CHARACTERISTIC: UUID = uuid16(0x2A06)
    val BATTERY_LEVEL_CHARACTERISTIC: UUID = uuid16(0x2A19)
    val CCCD: UUID = uuid16(0x2902)

    val ALERT_ON: ByteArray = byteArrayOf(1)
    val ALERT_OFF: ByteArray = byteArrayOf(0)

    sealed interface Message {
        data object ButtonPress : Message
        data class Unknown(val data: ByteArray) : Message
    }

    /** A notification from FFE1 is a button press when it is non-empty and value[0] == 1. */
    fun parseButtonNotification(data: ByteArray?): Message {
        if (data == null || data.isEmpty()) {
            return Message.Unknown(ByteArray(0))
        }
        return if (data[0] == BUTTON_PRESS_BYTE) Message.ButtonPress else Message.Unknown(data)
    }

    private const val BUTTON_PRESS_BYTE: Byte = 1

    private fun uuid16(short: Int): UUID =
        UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", short))
}
