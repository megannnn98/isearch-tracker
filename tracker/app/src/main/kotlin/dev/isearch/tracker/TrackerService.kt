package dev.isearch.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import dev.isearch.tracker.core.ButtonEventDetector
import dev.isearch.tracker.core.ConnectionSupervisor.State

/** Foreground service (type connectedDevice) that keeps the fob connected without any UI. */
class TrackerService : Service(), BleConnectionManager.Listener {
    private lateinit var prefs: Prefs
    private lateinit var alarm: PhoneAlarm
    private val detector = ButtonEventDetector()
    private var manager: BleConnectionManager? = null
    private var managerMac: String? = null
    private var fobAlerting = false
    private var state = State.Stopped
    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> manager?.onBluetoothOff()
                BluetoothAdapter.STATE_ON -> manager?.onBluetoothOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        alarm = PhoneAlarm(this)
        createChannel()
        if (!enterForeground()) {
            stopSelf()
            return
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                alarm.stop()
                refresh()
            }
            ACTION_TOGGLE_TEST_ALARM -> {
                toggleAlarm()
            }
            ACTION_TOGGLE_FOB_ALERT -> {
                val next = !fobAlerting
                if (manager?.sendAlert(next) == true) {
                    fobAlerting = next
                }
            }
            ACTION_STOP_SERVICE -> {
                prefs.serviceEnabled = false
                stopSelf()
            }
            else -> {
                prefs.serviceEnabled = true
                ensureManager()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
        }
        manager?.stop()
        manager = null
        alarm.stop()
        TrackerStatus.update(State.Stopped, false)
        super.onDestroy()
    }

    override fun onStateChanged(state: State) {
        this.state = state
        if (state != State.Ready) {
            detector.reset()
            fobAlerting = false
        }
        refresh()
    }

    override fun onButtonPress() {
        if (!prefs.findPhoneEnabled) {
            return
        }
        if (detector.onPress(SystemClock.elapsedRealtime())) {
            toggleAlarm()
        }
    }

    private fun toggleAlarm() {
        if (alarm.isPlaying) {
            alarm.stop()
        } else if (!alarm.start()) {
            Log.e(TAG, "alarm did not start")
        }
        refresh()
    }

    private fun ensureManager() {
        val mac = prefs.mac
        if (mac == null) {
            stopSelf()
            return
        }
        if (manager != null && managerMac == mac) {
            return
        }
        manager?.stop()
        managerMac = mac
        manager = BleConnectionManager(this, mac, this).also { it.start() }
    }

    private fun refresh() {
        TrackerStatus.update(state, alarm.isPlaying)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun enterForeground(): Boolean = try {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: RuntimeException) {
        // SecurityException (permission missing) or ForegroundServiceStartNotAllowedException.
        Log.e(TAG, "cannot enter foreground", e)
        false
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Брелок", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Поиск телефона")
            .setContentText(describe(state))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (alarm.isPlaying) {
            builder.addAction(Notification.Action.Builder(null, "Остановить сигнал", serviceIntent(ACTION_STOP_ALARM)).build())
        }
        builder.addAction(Notification.Action.Builder(null, "Выключить", serviceIntent(ACTION_STOP_SERVICE)).build())
        return builder.build()
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, TrackerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_STOP_ALARM = "dev.isearch.tracker.STOP_ALARM"
        const val ACTION_TOGGLE_TEST_ALARM = "dev.isearch.tracker.TOGGLE_TEST_ALARM"
        const val ACTION_TOGGLE_FOB_ALERT = "dev.isearch.tracker.TOGGLE_FOB_ALERT"
        const val ACTION_STOP_SERVICE = "dev.isearch.tracker.STOP_SERVICE"
        private const val CHANNEL_ID = "tracker"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "TrackerService"

        fun describe(state: State): String = when (state) {
            State.Stopped -> "Остановлено"
            State.Connecting -> "Ищу брелок…"
            State.Discovering -> "Подключаюсь…"
            State.Ready -> "Брелок подключён"
            State.WaitingRetry -> "Связь потеряна, повторяю"
            State.WaitingBluetooth -> "Bluetooth выключен"
        }

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, TrackerService::class.java)
            if (action != null) {
                intent.setAction(action)
            }
            context.startForegroundService(intent)
        }
    }
}
