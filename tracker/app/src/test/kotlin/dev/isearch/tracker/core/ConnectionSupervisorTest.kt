package dev.isearch.tracker.core

import dev.isearch.tracker.core.ConnectionSupervisor.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSupervisorTest {
    private class FakeDriver : ConnectionSupervisor.Driver {
        var opened = 0
        var closed = 0
        var discovers = 0
        var pending: Runnable? = null
        var pendingDelay: Long? = null
        val states = mutableListOf<State>()

        override fun openGatt() { opened++ }
        override fun closeGatt() { closed++ }
        override fun discoverServices() { discovers++ }
        override fun schedule(delayMs: Long, task: Runnable) {
            pending = task
            pendingDelay = delayMs
        }
        override fun cancelScheduled() {
            pending = null
            pendingDelay = null
        }
        override fun onStateChanged(state: State) { states.add(state) }

        fun fire() {
            val task = pending
            pending = null
            task?.run()
        }
    }

    private val driver = FakeDriver()
    private val supervisor = ConnectionSupervisor(
        driver,
        ReconnectPolicy(baseDelayMs = 1_000, maxDelayMs = 4_000),
        discoveryTimeoutMs = 5_000,
    )

    private fun connectToReady() {
        supervisor.start()
        supervisor.onConnected()
        supervisor.onServicesReady(true)
    }

    @Test
    fun happyPath() {
        connectToReady()
        assertEquals(State.Ready, supervisor.state)
        assertEquals(listOf(State.Connecting, State.Discovering, State.Ready), driver.states)
        assertEquals(1, driver.opened)
        assertEquals(1, driver.discovers)
        assertNull(driver.pending)
    }

    @Test
    fun disconnectTriggersBackoffAndReconnect() {
        connectToReady()
        supervisor.onDisconnected()
        assertEquals(State.WaitingRetry, supervisor.state)
        assertEquals(1, driver.closed)
        assertEquals(1_000L, driver.pendingDelay)
        driver.fire()
        assertEquals(State.Connecting, supervisor.state)
        assertEquals(2, driver.opened)
    }

    @Test
    fun repeatedFailuresBackOffAndCap() {
        supervisor.start()
        val delays = mutableListOf<Long>()
        repeat(5) {
            supervisor.onDisconnected()
            delays.add(driver.pendingDelay!!)
            driver.fire()
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 4_000L, 4_000L), delays)
    }

    @Test
    fun successfulReconnectResetsBackoff() {
        supervisor.start()
        supervisor.onDisconnected()
        driver.fire()
        supervisor.onDisconnected()
        driver.fire()
        supervisor.onConnected()
        supervisor.onServicesReady(true)
        supervisor.onDisconnected()
        assertEquals(1_000L, driver.pendingDelay)
    }

    @Test
    fun discoveryTimeoutClosesAndRetries() {
        supervisor.start()
        supervisor.onConnected()
        assertEquals(5_000L, driver.pendingDelay)
        driver.fire()
        assertEquals(State.WaitingRetry, supervisor.state)
        assertEquals(1, driver.closed)
        driver.fire()
        assertEquals(State.Connecting, supervisor.state)
        assertEquals(2, driver.opened)
    }

    @Test
    fun discoveryFailureIsRetried() {
        supervisor.start()
        supervisor.onConnected()
        supervisor.onServicesReady(false)
        assertEquals(State.WaitingRetry, supervisor.state)
        assertEquals(1_000L, driver.pendingDelay)
    }

    @Test
    fun lateDiscoveryResultIsIgnored() {
        supervisor.start()
        supervisor.onConnected()
        driver.fire()
        supervisor.onServicesReady(true)
        assertEquals(State.WaitingRetry, supervisor.state)
    }

    @Test
    fun bluetoothOffThenOnRestartsConnection() {
        connectToReady()
        supervisor.onBluetoothOff()
        assertEquals(State.WaitingBluetooth, supervisor.state)
        assertEquals(1, driver.closed)
        assertNull(driver.pending)
        supervisor.onBluetoothOn()
        assertEquals(State.Connecting, supervisor.state)
        assertEquals(2, driver.opened)
    }

    @Test
    fun disconnectWhileBluetoothOffDoesNotRetry() {
        connectToReady()
        supervisor.onBluetoothOff()
        supervisor.onDisconnected()
        assertEquals(State.WaitingBluetooth, supervisor.state)
        assertNull(driver.pending)
    }

    @Test
    fun stopCancelsRetryAndIgnoresLaterEvents() {
        supervisor.start()
        supervisor.onDisconnected()
        supervisor.stop()
        assertEquals(State.Stopped, supervisor.state)
        assertNull(driver.pending)
        supervisor.onBluetoothOn()
        supervisor.onConnected()
        assertEquals(State.Stopped, supervisor.state)
        assertEquals(1, driver.opened)
    }

    @Test
    fun staleRetryAfterStopDoesNothing() {
        supervisor.start()
        supervisor.onDisconnected()
        val stale = driver.pending
        supervisor.stop()
        stale?.run()
        assertEquals(1, driver.opened)
    }

    @Test
    fun startIsIdempotent() {
        supervisor.start()
        supervisor.start()
        assertEquals(1, driver.opened)
    }
}
