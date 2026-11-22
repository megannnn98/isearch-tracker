package dev.isearch.tracker.core

/**
 * Pure state machine behind BleConnectionManager: decides when to open/close GATT and when to retry.
 * All Android calls go through [Driver], so reconnect, timeout and error paths are unit-testable.
 * Not thread-safe: call from one thread (the main looper in the app).
 */
class ConnectionSupervisor(
    private val driver: Driver,
    private val policy: ReconnectPolicy = ReconnectPolicy(),
    private val discoveryTimeoutMs: Long = DISCOVERY_TIMEOUT_MS,
) {
    enum class State { Stopped, Connecting, Discovering, Ready, WaitingRetry, WaitingBluetooth }

    interface Driver {
        fun openGatt()
        fun closeGatt()
        fun discoverServices()

        /** Keeps at most one pending task: a new call replaces the previous one. */
        fun schedule(delayMs: Long, task: Runnable)
        fun cancelScheduled()
        fun onStateChanged(state: State)
    }

    var state: State = State.Stopped
        private set

    private var wanted = false

    fun start() {
        wanted = true
        if (state == State.Stopped) {
            policy.onSuccess()
            beginConnect()
        }
    }

    fun stop() {
        wanted = false
        driver.cancelScheduled()
        driver.closeGatt()
        setState(State.Stopped)
    }

    fun onBluetoothOff() {
        if (!wanted || state == State.WaitingBluetooth) {
            return
        }
        driver.cancelScheduled()
        driver.closeGatt()
        setState(State.WaitingBluetooth)
    }

    fun onBluetoothOn() {
        if (wanted && state == State.WaitingBluetooth) {
            policy.onSuccess()
            beginConnect()
        }
    }

    fun onConnected() {
        if (state != State.Connecting) {
            return
        }
        setState(State.Discovering)
        driver.schedule(discoveryTimeoutMs) { if (state == State.Discovering) fail() }
        driver.discoverServices()
    }

    fun onDisconnected() {
        if (state == State.Connecting || state == State.Discovering || state == State.Ready) {
            fail()
        }
    }

    fun onServicesReady(success: Boolean) {
        if (state != State.Discovering) {
            return
        }
        driver.cancelScheduled()
        if (success) {
            policy.onSuccess()
            setState(State.Ready)
        } else {
            fail()
        }
    }

    private fun fail() {
        driver.closeGatt()
        setState(State.WaitingRetry)
        driver.schedule(policy.onFailure()) {
            if (state == State.WaitingRetry) {
                beginConnect()
            }
        }
    }

    private fun beginConnect() {
        setState(State.Connecting)
        driver.openGatt()
    }

    private fun setState(newState: State) {
        if (state != newState) {
            state = newState
            driver.onStateChanged(newState)
        }
    }

    companion object {
        const val DISCOVERY_TIMEOUT_MS = 10_000L
    }
}
