package dev.isearch.tracker

import dev.isearch.tracker.core.ConnectionSupervisor.State

/** In-process status shared by the service and the activity. Touched only on the main thread. */
object TrackerStatus {
    var state: State = State.Stopped
        private set
    var alarmPlaying: Boolean = false
        private set
    var listener: (() -> Unit)? = null

    fun update(state: State = this.state, alarmPlaying: Boolean = this.alarmPlaying) {
        this.state = state
        this.alarmPlaying = alarmPlaying
        listener?.invoke()
    }
}
